package com.kgapp.encryptionchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecentViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    data class RecentState(
        val items: List<RecentItem> = emptyList()
    )

    data class RecentItem(
        val uid: String,
        val remark: String,
        val lastText: String,
        val lastTime: String,
        val lastTs: String
    )

    private val _state = MutableStateFlow(RecentState())
    val state: StateFlow<RecentState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val recents = repository.getRecentChats().map { item ->
                RecentItem(
                    uid = item.uid,
                    remark = item.remark,
                    lastText = item.lastText,
                    lastTime = TimeFormatter.formatTimestamp(item.lastTs),
                    lastTs = item.lastTs
                )
            }
            _state.value = RecentState(recents)
        }
    }

    fun deleteChat(uid: String) {
        viewModelScope.launch {
            repository.deleteChatHistory(uid)
            refresh()
        }
    }
}
