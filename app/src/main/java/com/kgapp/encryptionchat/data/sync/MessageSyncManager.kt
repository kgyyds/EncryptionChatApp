package com.kgapp.encryptionchat.data.sync

import android.content.Context
import android.util.Log
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.Api2Client
import com.kgapp.encryptionchat.util.UnreadCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import org.json.JSONObject
import java.time.Instant
import com.kgapp.encryptionchat.notifications.MessageSyncService
import com.kgapp.encryptionchat.util.NotificationPreferences

class MessageSyncManager(
    private val repository: ChatRepository,
    private val context: Context,
    private val api: Api2Client
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var chatFromUid: String? = null
    private var chatJob: Job? = null
    private var chatCall: Call? = null
    private var broadcastJob: Job? = null
    private var broadcastCall: Call? = null
    private var activeChatUid: String? = null

    val updates: SharedFlow<String> = MessageUpdateBus.updates

    suspend fun refreshOnce(fromUid: String): String? {
        val result = repository.readChat(fromUid)
        return when {
            result.handshakeFailed -> {
                repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
                MessageUpdateBus.emit(fromUid)
                "握手密码错误"
            }
            !result.success -> {
                if (result.message == "无新消息") {
                    null
                } else {
                    result.message
                }
            }
            else -> {
                if (result.addedCount > 0) {
                    MessageUpdateBus.emit(fromUid)
                    if (activeChatUid != fromUid) {
                        UnreadCounter.increment(context, fromUid)
                    }
                }
                null
            }
        }
    }

    suspend fun refreshRecentChats(): String? {
        val recents = repository.getRecentChats()
        var lastError: String? = null
        for (item in recents) {
            val message = refreshOnce(item.uid)
            if (!message.isNullOrBlank()) {
                lastError = message
            }
        }
        return lastError
    }

    suspend fun startChatSse(fromUid: String) {
        mutex.withLock {
            if (chatFromUid == fromUid && chatJob?.isActive == true) {
                return
            }
            stopChatSseLocked()
            chatFromUid = fromUid
            activeChatUid = fromUid
            chatJob = scope.launch {
                val lastTs = repository.getLastTimestamp(fromUid)
                val payload = mapOf(
                    "type" to 2,
                    "from" to fromUid,
                    "last_ts" to lastTs
                )
                val call = api.openSseStream(payload)
                    ?: run {
                        Log.d(TAG, "SSE skipped: missing credentials")
                        return@launch
                    }
                chatCall = call
                Log.d(TAG, "Chat SSE start for $fromUid with lastTs=$lastTs")
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.d(TAG, "SSE response error: ${response.code}")
                            return@use
                        }
                        val source = response.body?.source() ?: return@use
                        var pendingData: String? = null
                        while (!source.exhausted() && chatCall?.isCanceled() != true) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) {
                                if (!pendingData.isNullOrBlank()) {
                                    handleChatSseData(fromUid, pendingData)
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
                    if (chatCall?.isCanceled() != true) {
                        Log.d(TAG, "SSE error: ${ex.message}")
                    }
                }
            }
        }
    }

    fun ensureBroadcastSseRunning() {
        scope.launch {
            mutex.withLock {
                if (NotificationPreferences.isBackgroundReceiveEnabled(context) || MessageSyncService.isRunning) {
                    return@withLock
                }
                if (broadcastJob?.isActive == true) {
                    return@withLock
                }
                startBroadcastSseLocked()
            }
        }
    }

    suspend fun startBroadcastSse() {
        mutex.withLock {
            if (NotificationPreferences.isBackgroundReceiveEnabled(context) || MessageSyncService.isRunning) {
                return
            }
            if (broadcastJob?.isActive == true) {
                return
            }
            startBroadcastSseLocked()
        }
    }

    suspend fun stopChatSse() {
        mutex.withLock {
            stopChatSseLocked()
        }
    }

    suspend fun stopBroadcastSse() {
        mutex.withLock {
            stopBroadcastSseLocked()
        }
    }

    private suspend fun stopChatSseLocked() {
        chatCall?.cancel()
        chatCall = null
        chatFromUid = null
        activeChatUid = null
        chatJob?.cancelAndJoin()
        chatJob = null
        Log.d(TAG, "Chat SSE stopped")
    }

    private suspend fun stopBroadcastSseLocked() {
        broadcastCall?.cancel()
        broadcastCall = null
        broadcastJob?.cancelAndJoin()
        broadcastJob = null
        Log.d(TAG, "Broadcast SSE stopped")
    }

    private suspend fun startBroadcastSseLocked() {
        activeChatUid = null
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

    private suspend fun handleChatSseData(fromUid: String, payload: String) {
        val json = JSONObject(payload)
        val text = json.optString("text", "")
        val ts = json.optLong("ts", 0L)
        if (text.isBlank() || ts <= 0L) return
        val result = repository.handleIncomingCipherMessage(fromUid, ts, text)
        if (result.handshakeFailed) {
            repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
        }
        if (result.success) {
            MessageUpdateBus.emit(fromUid)
            if (activeChatUid != fromUid) {
                UnreadCounter.increment(context, fromUid)
            }
        }
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
            if (activeChatUid != fromUid) {
                UnreadCounter.increment(context, fromUid)
            }
        }
    }

    companion object {
        private const val TAG = "MessageSyncManager"
    }

}
