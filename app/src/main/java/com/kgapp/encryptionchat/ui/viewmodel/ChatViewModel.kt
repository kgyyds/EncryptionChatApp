package com.kgapp.encryptionchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgapp.encryptionchat.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    data class ChatState(
        val uid: String = "",
        val remark: String = "",
        val selfName: String = "",
        val messages: List<UiMessage> = emptyList(),
        val hasHistory: Boolean = true
    )

    data class UiMessage(
        val ts: String,
        val speaker: Int,
        val text: String
    )

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun load(uid: String) {
    _state.value = _state.value.copy(uid = uid)
    viewModelScope.launch {
        val contacts = repository.readContacts()
        val contact = contacts[uid]
        val remark = contact?.Remark ?: uid

        val history = repository.readChatHistory(uid)
        val selfName = repository.getSelfName().orEmpty().ifBlank { "我" }

        val filteredHistory = history.filter { (ts, message) ->
            val epoch = ts.toLongOrNull() ?: 0L
            epoch > 0L && message.text != "暂无记录"
        }

    
        val uiMessages = filteredHistory
    .map { (ts, message) ->
        UiMessage(
            ts = ts,
            speaker = message.Spokesman,
            text = message.text
        )
    }
    .sortedByDescending { it.ts.toLongOrNull() ?: 0L } 

        _state.value = ChatState(
            uid = uid,
            remark = remark,
            selfName = selfName,
            messages = uiMessages,
            hasHistory = filteredHistory.isNotEmpty()
        )
    }
}

    fun refresh() {
        val uid = _state.value.uid
        if (uid.isBlank()) return
        load(uid)
    }

    suspend fun sendMessage(text: String): String? {
        val uid = _state.value.uid
        if (uid.isBlank()) return null
        val localTs = Instant.now().epochSecond.toString()
        repository.appendMessage(uid, localTs, 0, text)
        refresh()
        val result = repository.sendChat(uid, text) { attempt ->
            repository.appendMessage(uid, Instant.now().epochSecond.toString(), 2, "消息发送重试中(第${attempt}次)")
        }
        return if (result.success) {
            val serverTs = result.addedTs
            if (!serverTs.isNullOrBlank() && serverTs != localTs) {
                repository.replaceMessageTimestamp(uid, localTs, serverTs, 0, text)
            }
            refresh()
            null
        } else {
            repository.appendMessage(uid, Instant.now().epochSecond.toString(), 2, "消息发送失败")
            refresh()
            result.message ?: "消息发送失败"
        }
    }

    suspend fun readNewMessages(showNoNewMessageHint: Boolean = true): String? {
        val uid = _state.value.uid
        if (uid.isBlank()) return null
        val result = repository.readChat(uid)
        return when {
            result.handshakeFailed -> {
                repository.appendMessage(uid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
                refresh()
                "握手密码错误"
            }
            !result.success -> {
                val message = result.message ?: "网络连接异常"
                if (message != "无新消息") {
                    repository.appendMessage(uid, Instant.now().epochSecond.toString(), 2, message)
                    refresh()
                    message
                } else if (showNoNewMessageHint) {
                    "无新消息"
                } else {
                    null
                }
            }
            else -> {
                refresh()
                null
            }
        }
    }
    suspend fun deleteMessage(ts: String) {
    val uid = _state.value.uid
    if (uid.isBlank() || ts.isBlank()) return
    repository.deleteMessage(uid, ts)
    refresh()
}

}
