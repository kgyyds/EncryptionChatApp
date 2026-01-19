package com.kgapp.encryptionchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgapp.encryptionchat.data.ChatRepository
import com.kgapp.encryptionchat.data.model.ContactConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    data class ContactsState(
        val contacts: List<Pair<String, ContactConfig>> = emptyList(),
        val hasKeys: Boolean = false
    )

    private val _state = MutableStateFlow(ContactsState())
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val contacts = repository.readContacts().toList()
            val hasKeys = repository.hasKeyPair()
            _state.value = ContactsState(contacts = contacts, hasKeys = hasKeys)
        }
    }

    fun addContact(remark: String, pubKey: String, password: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val uid = repository.addContact(remark, pubKey, password)
            refresh()
            onResult(uid)
        }
    }

    fun updateRemark(uid: String, remark: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.updateContactRemark(uid, remark)
            refresh()
            onComplete(success)
        }
    }
}
