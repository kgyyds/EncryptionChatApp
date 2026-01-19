package com.kgapp.encryptionchat.data.sync

import android.util.Log
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.api.SseChatApi
import com.kgapp.encryptionchat.util.PullMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import org.json.JSONObject
import java.time.Instant

class MessageSyncManager(
    private val repository: ChatRepository,
    private val sseApi: SseChatApi = SseChatApi()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var currentFromUid: String? = null
    private var currentJob: Job? = null
    private var currentCall: Call? = null
    private var currentMode: PullMode = PullMode.CHAT_SSE
    private var lastActiveChatUid: String? = null

    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val updates: SharedFlow<String> = _updates

    suspend fun refreshOnce(fromUid: String): String? {
        val result = repository.readChat(fromUid)
        return when {
            result.handshakeFailed -> {
                repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
                _updates.tryEmit(fromUid)
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
                    _updates.tryEmit(fromUid)
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

    fun updateMode(mode: PullMode, activeChatUid: String?) {
        currentMode = mode
        if (activeChatUid != null) {
            lastActiveChatUid = activeChatUid
        }
        scope.launch {
            when (mode) {
                PullMode.MANUAL -> stopSse()
                PullMode.CHAT_SSE -> {
                    if (activeChatUid != null) {
                        startSse(activeChatUid)
                    } else {
                        stopSse()
                    }
                }
                PullMode.GLOBAL_SSE -> {
                    val targetUid = activeChatUid ?: lastActiveChatUid ?: repository.getRecentChats().firstOrNull()?.uid
                    if (targetUid != null) {
                        startSse(targetUid)
                    } else {
                        stopSse()
                    }
                }
            }
        }
    }

    suspend fun stopSse() {
        mutex.withLock {
            currentCall?.cancel()
            currentCall = null
            currentFromUid = null
            currentJob?.cancelAndJoin()
            currentJob = null
            Log.d(TAG, "SSE stopped")
        }
    }

    suspend fun startSse(fromUid: String) {
        mutex.withLock {
            if (currentFromUid == fromUid && currentJob?.isActive == true) {
                return
            }
            currentCall?.cancel()
            currentJob?.cancelAndJoin()
            currentFromUid = fromUid
            currentJob = scope.launch {
                val lastTs = repository.getLastTimestamp(fromUid)
                val payload = buildSsePayload(fromUid, lastTs)
                if (payload.isEmpty()) {
                    Log.d(TAG, "SSE skipped: missing credentials")
                    return@launch
                }
                val call = sseApi.openStream(payload)
                currentCall = call
                Log.d(TAG, "SSE start for $fromUid with lastTs=$lastTs")
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.d(TAG, "SSE response error: ${response.code}")
                            return@use
                        }
                        val source = response.body?.source() ?: return@use
                        var pendingData: String? = null
                        while (!source.exhausted() && currentCall?.isCanceled() != true) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) {
                                if (!pendingData.isNullOrBlank()) {
                                    handleSseData(fromUid, pendingData)
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
                    if (currentCall?.isCanceled() != true) {
                        Log.d(TAG, "SSE error: ${ex.message}")
                    }
                }
            }
        }
    }

    private suspend fun buildSsePayload(fromUid: String, lastTs: Long): Map<String, String> {
        val pemB64 = repository.getPemBase64() ?: return emptyMap()
        val (ts, sig) = repository.signNow()
        return mapOf(
            "ts" to ts,
            "sig" to sig,
            "pub" to pemB64,
            "type" to "2",
            "from" to fromUid,
            "last_ts" to lastTs.toString()
        )
    }

    private suspend fun handleSseData(fromUid: String, payload: String) {
        val json = JSONObject(payload)
        val text = json.optString("text", "")
        val ts = json.optLong("ts", 0L)
        if (text.isBlank() || ts <= 0L) return
        val result = repository.handleIncomingCipherMessage(fromUid, ts, text)
        if (result.handshakeFailed) {
            repository.appendMessage(fromUid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
        }
        if (result.success) {
            _updates.tryEmit(fromUid)
        }
    }

    companion object {
        private const val TAG = "MessageSyncManager"
    }
}
