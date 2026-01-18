package com.kgapp.encryptionchat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ContactConfig(
    val Remark: String,
    val public: String,
    val pass: String
)

@Serializable
data class ChatMessage(
    val Spokesman: Int,
    val text: String
)
