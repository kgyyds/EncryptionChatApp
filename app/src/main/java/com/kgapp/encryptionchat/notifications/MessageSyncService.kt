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
import kotlinx.coroutines.launch
import okhttp3.Call
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

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
            val contacts = repository.readContacts()
            if (contacts.isEmpty()) {
                Log.d(TAG, "Broadcast SSE skipped: no contacts")
                return@launch
            }
            val contactPayload = contacts.keys.map { uid ->
                val lastTs = repository.getLastTimestamp(uid)
                mapOf("uid" to uid, "ts" to lastTs)
            }
            val payload = mapOf(
                "type" to 3,
                "contacts" to contactPayload
            )
            val call = api.openSseStream(payload)
                ?: run {
                    Log.d(TAG, "Broadcast SSE skipped: missing credentials")
                    return@launch
                }
            broadcastCall = call
            Log.d(TAG, "Broadcast SSE start with ${contactPayload.size} contacts")
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.d(TAG, "SSE response error: ${response.code}")
                        return@use
                    }
                    val source = response.body?.source() ?: return@use
                    var pendingData: String? = null
                    while (!source.exhausted() && broadcastCall?.isCanceled() != true) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) {
                            if (!pendingData.isNullOrBlank()) {
                                handleBroadcastSseData(pendingData)
                                pendingData = null
                            }
                            continue
                        }
                        if (line == "hb") {
                            continue
                        }
                        if (line.startsWith("data: ")) {
                            pendingData = line.removePrefix("data: ").trim()
                        }
                    }
                }
            } catch (ex: Exception) {
                if (broadcastCall?.isCanceled() != true) {
                    Log.d(TAG, "SSE error: ${ex.message}")
                }
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
        if (fromUid.isBlank() || text.isBlank() || ts <= 0L) return
        val result = repository.handleIncomingCipherMessage(fromUid, ts, text)
        if (result.handshakeFailed) {
            repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
        }
        if (result.success) {
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
        notifier.notifyMessage(fromUid, title, preview, ts, unreadCount, locked)
    }

    companion object {
        private const val TAG = "MessageSyncService"
        private const val NOTIFICATION_ID = 2001
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
