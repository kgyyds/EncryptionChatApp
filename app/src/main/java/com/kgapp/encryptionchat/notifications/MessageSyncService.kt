package com.kgapp.encryptionchat.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.Api2Client
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.storage.FileStorage
import com.kgapp.encryptionchat.data.sync.MessageSyncRegistry
import com.kgapp.encryptionchat.data.sync.MessageUpdateBus
import com.kgapp.encryptionchat.security.SecuritySettings
import com.kgapp.encryptionchat.util.ApiSettingsPreferences
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.net.SocketTimeoutException

class MessageSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null
    private var broadcastCall: Call? = null
    private lateinit var repository: ChatRepository
    private lateinit var api: Api2Client
    private lateinit var notifier: AppNotifier

    override fun onCreate() {
        super.onCreate()
        ApiSettingsPreferences.initialize(this)
        NotificationPreferences.initialize(this)
        UnreadCounter.initialize(this)
        val storage = FileStorage(this)
        val crypto = CryptoManager(storage)
        api = Api2Client(
            crypto = crypto,
            baseUrlProvider = { ApiSettingsPreferences.getBaseUrl(this) }
        )
        repository = ChatRepository(storage, crypto, api)
        notifier = AppNotifier(this)
        notifier.ensureChannels()
        startForeground(NOTIFICATION_ID, notifier.notifyServiceRunning())
        isRunningFlag.set(true)
        MessageSyncRegistry.stopAppBroadcast()
        startBroadcastSse()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!NotificationPreferences.isBackgroundReceiveEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
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
                val payload = mapOf(
                    "type" to 3,
                    "contacts" to contactPayload
                )
                val tsSummary = contactPayload.joinToString(limit = 5) { item ->
                    val uid = item["uid"]?.toString().orEmpty()
                    val ts = item["ts"]?.toString().orEmpty()
                    "$uid:$ts"
                }
                Log.d(
                    TAG,
                    "Broadcast SSE request url=${ApiSettingsPreferences.getBaseUrl(this@MessageSyncService)} " +
                        "type=3 contacts=${contactPayload.size} ts=$tsSummary"
                )
                val call = api.openSseStream(payload)
                if (call == null) {
                    Log.d(TAG, "Broadcast SSE skipped: missing credentials")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                broadcastCall = call
                Log.d(TAG, "Broadcast SSE start with ${contactPayload.size} contacts")
                try {
                    call.execute().use { response ->
                        val contentType = response.header("Content-Type")
                        Log.d(TAG, "SSE response code=${response.code} contentType=$contentType")
                        if (!response.isSuccessful) {
                            Log.d(TAG, "SSE response error: ${response.code}")
                            return@use
                        }
                        if (contentType?.contains("text/event-stream", ignoreCase = true) != true) {
                            Log.d(TAG, "SSE content-type mismatch: $contentType")
                            return@use
                        }
                        val source = response.body?.source() ?: return@use
                        var pendingData: String? = null
                        while (!source.exhausted() && broadcastCall?.isCanceled() != true) {
                            val line = source.readUtf8Line() ?: break
                            if (line == "hb") {
                                Log.d(TAG, "SSE line: hb")
                                continue
                            }
                            if (line.startsWith("data: ")) {
                                Log.d(TAG, "SSE line: ${line.take(200)}")
                            }
                            if (line.isBlank()) {
                                if (!pendingData.isNullOrBlank()) {
                                    handleBroadcastSseData(pendingData)
                                    pendingData = null
                                }
                                continue
                            }
                            if (line.startsWith("data: ")) {
                                pendingData = line.removePrefix("data: ").trim()
                            }
                        }
                    }
                    backoffMs = 1000L
                } catch (ex: Exception) {
                    val canceled = broadcastCall?.isCanceled() == true
                    val timeout = ex is SocketTimeoutException
                    Log.d(TAG, "SSE error: ${ex.message} timeout=$timeout canceled=$canceled", ex)
                } finally {
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
        val text = json.optString("text", "")
        val ts = json.optLong("ts", 0L)
        Log.d(TAG, "SSE payload parsed from=$fromUid ts=$ts")
        if (fromUid.isBlank() || text.isBlank() || ts <= 0L) return
        val result = repository.handleIncomingCipherMessage(fromUid, ts, text)
        if (result.handshakeFailed) {
            repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
        }
        if (result.success) {
            Log.d(TAG, "SSE message saved uid=$fromUid ts=$ts")
            MessageUpdateBus.emit(fromUid)
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
        Log.d(TAG, "Notify message uid=$fromUid locked=$locked preview=$previewMode id=$notificationId")
        notifier.notifyMessage(fromUid, title, preview, ts, unreadCount, locked)
    }

    companion object {
        private const val TAG = "MessageSyncService"
        private const val NOTIFICATION_ID = 1
        private const val MAX_BACKOFF_MS = 30000L
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
