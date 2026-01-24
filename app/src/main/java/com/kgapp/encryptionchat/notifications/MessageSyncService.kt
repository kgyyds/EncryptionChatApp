package com.kgapp.encryptionchat.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.Api4Client
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.storage.FileStorage
import com.kgapp.encryptionchat.data.sync.MessageSyncRegistry
import com.kgapp.encryptionchat.data.sync.MessageUpdateBus
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.util.ApiSettingsPreferences
import com.kgapp.encryptionchat.util.DebugLevel
import com.kgapp.encryptionchat.util.DebugLog
import com.kgapp.encryptionchat.util.DebugPreferences
import com.kgapp.encryptionchat.util.NotificationPreferences
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class MessageSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var broadcastJob: Job? = null
    private var broadcastCall: Call? = null

    private lateinit var repository: ChatRepository
    private lateinit var api: Api4Client
    private lateinit var notifier: AppNotifier

    override fun onCreate() {
        super.onCreate()

        ApiSettingsPreferences.initialize(this)
        NotificationPreferences.initialize(this)
        UnreadCounter.initialize(this)

        val storage = FileStorage(this)
        val crypto = CryptoManager(storage)
        api = Api4Client(
            crypto = crypto,
            baseUrlProvider = { ApiSettingsPreferences.getBaseUrl(this) }
        )
        repository = ChatRepository(storage, crypto, api)

        notifier = AppNotifier(this)
        notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!NotificationPreferences.isBackgroundReceiveEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(SERVICE_NOTIFICATION_ID, notifier.notifyServiceRunning())

        if (isRunningFlag.compareAndSet(false, true)) {
            MessageSyncRegistry.stopAppBroadcast()
            startBroadcastSse()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch {
            stopBroadcastSse()
        }
        isRunningFlag.set(false)
        MessageSyncRegistry.ensureAppBroadcast()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBroadcastSse() {
        if (broadcastJob?.isActive == true) return

        broadcastJob = scope.launch {
            var backoffMs = 1000L

            while (isActive) {

                val contacts = repository.readContacts()
                if (contacts.isEmpty()) {
                    Log.d(TAG, "Broadcast SSE skipped: no contacts")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }

                val contactPayload = contacts.keys.map { uid ->
                    val lastTs = repository.getLastTimestamp(uid)
                    mapOf("uid" to uid, "ts" to lastTs)
                }

                val tsSummary = contactPayload.joinToString(limit = 5) { item ->
                    val uid = item["uid"]?.toString().orEmpty()
                    val ts = item["ts"]?.toString().orEmpty()
                    "$uid:$ts"
                }

                Log.d(
                    TAG,
                    "Broadcast SSE request url=${ApiSettingsPreferences.getBaseUrl(this@MessageSyncService)} " +
                        "type=SseAllMsg contacts=${contactPayload.size} ts=$tsSummary"
                )

                val call = api.openSseAllMsg(contactPayload)
                if (call == null) {
                    Log.d(TAG, "Broadcast SSE skipped: missing credentials")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }

                broadcastCall = call

                var lastAliveAt = System.currentTimeMillis()
                var watchdogJob: Job? = null

                // ✅ 独立 watchdog：即使 readUtf8Line() 卡死，也会 cancel 逼出重连
                watchdogJob = launch {
                    while (isActive && broadcastCall === call) {
                        delay(WATCHDOG_TICK_MS)
                        val idle = System.currentTimeMillis() - lastAliveAt
                        if (idle > WATCHDOG_IDLE_MS) {
                            Log.d(TAG, "SSE watchdog: idle=${idle}ms > ${WATCHDOG_IDLE_MS}ms, cancel & reconnect")
                            call.cancel()
                            break
                        }
                    }
                }

                Log.d(TAG, "Broadcast SSE start with ${contactPayload.size} contacts")

                try {
                    call.execute().use { response ->
                        val contentType = response.header("Content-Type")
                        Log.d(TAG, "SSE response code=${response.code} contentType=$contentType")

                        if (!response.isSuccessful) {
                            val body = response.body?.string()?.take(500)
                            Log.d(TAG, "SSE response error: ${response.code} body=$body")
                            // 让外层 backoff 重连
                            return@use
                        }

                        if (contentType?.contains("text/event-stream", ignoreCase = true) != true) {
                            val body = response.body?.string()?.take(500)
                            Log.d(TAG, "SSE content-type mismatch: $contentType body=$body")
                            return@use
                        }

                        val source = response.body?.source() ?: return@use
                        var pendingData: StringBuilder? = null

                        while (!source.exhausted() && call.isCanceled().not()) {
                            val line = source.readUtf8Line() ?: break

                            // ✅ 只要读到任何行就算活跃（最稳）
                            lastAliveAt = System.currentTimeMillis()

                            if (line == "hb") {
                                Log.d(TAG, "SSE line: hb")
                                continue
                            }

                            if (line.startsWith("data:")) {
                                Log.d(TAG, "SSE line: ${line.take(200)}")
                            }

                            // SSE 事件结束（空行）-> 处理聚合 data
                            if (line.isBlank()) {
                                if (pendingData?.isNotEmpty() == true) {
                                    handleBroadcastSseData(pendingData.toString())
                                }
                                pendingData = null
                                continue
                            }

                            if (line.startsWith("data:")) {
                                val chunk = line.removePrefix("data:").trimStart()
                                val builder = pendingData ?: StringBuilder().also { pendingData = it }
                                if (builder.isNotEmpty()) builder.append("\n")
                                builder.append(chunk)
                            }
                        }
                    }

                    // ✅ 只要跑到这里说明这轮连接“正常结束/被取消/断开”，重置 backoff
                    backoffMs = 1000L

                } catch (ex: Exception) {
                    val canceled = call.isCanceled()
                    val timeout = ex is SocketTimeoutException
                    Log.d(TAG, "SSE error: ${ex.message} timeout=$timeout canceled=$canceled", ex)

                } finally {
                    watchdogJob?.cancel()
                    watchdogJob = null
                    broadcastCall = null
                }

                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private suspend fun stopBroadcastSse() {
        broadcastCall?.cancel()
        broadcastCall = null
        broadcastJob?.cancelAndJoin()
        broadcastJob = null
    }

    private suspend fun handleBroadcastSseData(payload: String) {
        val json = JSONObject(payload)
        val fromUid = json.optString("from", "")
        val key = json.optString("key", "")
        val text = json.optString("msg", "")
        val ts = json.optLong("ts", 0L)

        val detailed = DebugPreferences.isDetailedLogs(this)
        DebugLog.append(
            context = this,
            level = DebugLevel.INFO,
            tag = "SSE",
            chatUid = fromUid,
            eventName = "payload",
            message = "len=${payload.length} from=$fromUid ts=$ts keyLen=${key.length} msgLen=${text.length}",
            optionalJson = DebugLog.optionalJson(
                mapOf(
                    "from" to fromUid,
                    "ts" to ts,
                    "keySummary" to DebugLog.summarizeSensitive(key, detailed),
                    "msgSummary" to DebugLog.summarizeSensitive(text, detailed)
                ),
                detailed
            )
        )

        Log.d(TAG, "SSE payload parsed from=$fromUid ts=$ts")
        if (fromUid.isBlank() || text.isBlank() || ts <= 0L) return

        val result = repository.handleIncomingCipherMessage(fromUid, ts, key, text)
        if (result.handshakeFailed) {
            repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
        }

        if (result.success) {
            Log.d(TAG, "SSE message saved uid=$fromUid ts=$ts")
            MessageUpdateBus.emit(fromUid, this)
            UnreadCounter.increment(this, fromUid)
            notifyIncomingMessage(fromUid, ts)
        }
    }

    private suspend fun notifyIncomingMessage(fromUid: String, ts: Long) {
        if (!NotificationPreferences.isNotificationsEnabled(this)) return

        val contact = repository.getContact(fromUid)
        val title = contact?.Remark?.takeIf { it.isNotBlank() } ?: fromUid

        val history = repository.readChatHistory(fromUid)
        val message = history[ts.toString()]
        val preview = message?.text ?: ""

        val unreadCount = UnreadCounter.counts.value[fromUid] ?: 1
        val locked = SecuritySettings.readConfig(this).appLockEnabled
        val previewMode = NotificationPreferences.getPreviewMode(this)
        val notificationId = fromUid.hashCode()

        Log.d(TAG, "Notify message uid=$fromUid locked=$locked preview=$previewMode unread=$unreadCount id=$notificationId")

        notifier.notifyMessage(
            fromUid = fromUid,
            fromName = title,
            preview = preview,
            ts = ts
        )
    }

    companion object {
        private const val TAG = "MessageSyncService"

        const val SERVICE_NOTIFICATION_ID = 1
        private const val MAX_BACKOFF_MS = 30000L

        // ✅ watchdog 配置：建议和 Api4Client 的 readTimeout 对齐
        // 如果你 readTimeout=75s，这里 idle 设 80s 很合适
        private const val WATCHDOG_TICK_MS = 5_000L
        private const val WATCHDOG_IDLE_MS = 35_000L

        private val isRunningFlag = AtomicBoolean(false)

        val isRunning: Boolean
            get() = isRunningFlag.get()

        fun start(context: Context) {
            if (isRunningFlag.get()) return
            val intent = Intent(context, MessageSyncService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageSyncService::class.java))
        }
    }
}