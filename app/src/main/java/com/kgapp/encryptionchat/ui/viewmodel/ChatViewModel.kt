package com.kgapp.encryptionchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.model.ChatMessage
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
        val messages: List<Pair<String, ChatMessage>> = emptyList()
    )

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun load(uid: String) {
        viewModelScope.launch {
            val contacts = repository.readContacts()
            val remark = contacts[uid]?.Remark ?: uid
            val history = repository.readChatHistory(uid)
                .toList()
                .sortedBy { it.first.toLongOrNull() ?: 0L }
            _state.value = ChatState(uid = uid, remark = remark, messages = history)
        }
    }

    fun refresh() {
        val uid = _state.value.uid
        if (uid.isBlank()) return
        load(uid)
    }

    suspend fun sendMessage(text: String): String? {
        val uid = _state.value.uid
        if (uid.isBlank()) return "联系人无效"
        val localTs = Instant.now().epochSecond.toString()
        repository.appendMessage(uid, localTs, 0, text)
        refresh()
        val result = repository.sendChat(uid, text)
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

    suspend fun readNewMessages(): String? {
        val uid = _state.value.uid
        if (uid.isBlank()) return "联系人无效"
        val result = repository.readChat(uid)
        return when {
            result.handshakeFailed -> {
                repository.appendMessage(uid, Instant.now().epochSecond.toString(), 2, "握手密码错误")
                refresh()
                null
            }
            !result.success -> {
                repository.appendMessage(uid, Instant.now().epochSecond.toString(), 2, result.message ?: "网络连接异常")
                refresh()
                null
            }
            else -> {
                refresh()
                null
            }
        }
    }
}
