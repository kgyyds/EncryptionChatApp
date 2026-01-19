package com.kgapp.encryptionchat.data

import com.kgapp.encryptionchat.data.api.ApiResult
import com.kgapp.encryptionchat.data.api.ChatApi
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.model.ChatMessage
import com.kgapp.encryptionchat.data.model.ContactConfig
import com.kgapp.encryptionchat.data.storage.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChatRepository(
    private val storage: FileStorage,
    private val crypto: CryptoManager,
    private val api: ChatApi
) {
    data class SendResult(
        val success: Boolean,
        val serverCode: Int?,
        val message: String?,
        val addedTs: String?
    )

    data class ReadResult(
        val success: Boolean,
        val serverCode: Int?,
        val message: String?,
        val addedCount: Int,
        val handshakeFailed: Boolean
    )

    suspend fun hasPrivateKey(): Boolean = withContext(Dispatchers.IO) {
        crypto.hasPrivateKey()
    }

    suspend fun hasPublicKey(): Boolean = withContext(Dispatchers.IO) {
        crypto.hasPublicKey()
    }

    suspend fun hasKeyPair(): Boolean = withContext(Dispatchers.IO) {
        crypto.hasKeyPair()
    }

    suspend fun generateKeyPair(): Boolean = withContext(Dispatchers.IO) {
        crypto.generateKeyPair()
    }

    suspend fun getSelfName(): String? = withContext(Dispatchers.IO) {
        crypto.computeSelfName()
    }

    suspend fun getPublicPemText(): String? = withContext(Dispatchers.IO) {
        storage.readPublicPemText()
    }

    suspend fun importPrivatePem(pemText: String): Boolean = withContext(Dispatchers.IO) {
        crypto.importPrivatePem(pemText)
    }

    suspend fun importPublicPem(pemText: String): Boolean = withContext(Dispatchers.IO) {
        crypto.importPublicPem(pemText)
    }

    suspend fun getPemBase64(): String? = withContext(Dispatchers.IO) {
        crypto.computePemBase64()
    }

    suspend fun readContacts(): Map<String, ContactConfig> = withContext(Dispatchers.IO) {
        storage.readContactsConfig()
    }

    suspend fun updateContactRemark(uid: String, remark: String): Boolean = withContext(Dispatchers.IO) {
        storage.updateContactRemark(uid, remark)
    }

    suspend fun readContactsRaw(): String = withContext(Dispatchers.IO) {
        storage.readContactsConfigRaw()
    }

    suspend fun addContact(remark: String, pubKey: String, password: String): String {
        return withContext(Dispatchers.IO) {
            val pubB64 = java.util.Base64.getEncoder().encodeToString(pubKey.toByteArray(Charsets.UTF_8))
            val uid = crypto.md5Hex(pubB64.toByteArray(Charsets.UTF_8))
            val config = storage.readContactsConfig()
            config[uid] = ContactConfig(Remark = remark, public = pubB64, pass = password)
            storage.writeContactsConfig(config)
            storage.ensureChatFile(uid)
            uid
        }
    }

    suspend fun readChatHistory(uid: String): Map<String, ChatMessage> = withContext(Dispatchers.IO) {
        storage.readChatHistory(uid)
    }

    suspend fun deleteChatHistory(uid: String) = withContext(Dispatchers.IO) {
        storage.deleteChatHistory(uid)
    }

    data class RecentChat(
        val uid: String,
        val remark: String,
        val lastText: String,
        val lastTs: String
    )

    suspend fun getRecentChats(): List<RecentChat> = withContext(Dispatchers.IO) {
        val contacts = storage.readContactsConfig()
        val chatFiles = storage.listChatFiles()
        val recents = chatFiles.mapNotNull { file ->
            val uid = file.nameWithoutExtension
            val history = storage.readChatHistory(uid)
            val lastEntry = history.maxByOrNull { it.key.toLongOrNull() ?: 0L } ?: return@mapNotNull null
            val remark = contacts[uid]?.Remark ?: uid
            RecentChat(
                uid = uid,
                remark = remark,
                lastText = lastEntry.value.text,
                lastTs = lastEntry.key
            )
        }
        recents.sortedByDescending { it.lastTs.toLongOrNull() ?: 0L }
    }

    suspend fun appendMessage(uid: String, ts: String, speaker: Int, text: String) = withContext(Dispatchers.IO) {
        storage.upsertChatMessage(uid, ts, ChatMessage(Spokesman = speaker, text = text))
    }

    suspend fun replaceMessageTimestamp(uid: String, oldTs: String, newTs: String, speaker: Int, text: String) =
        withContext(Dispatchers.IO) {
            storage.replaceChatTimestamp(uid, oldTs, newTs, ChatMessage(Spokesman = speaker, text = text))
        }

    suspend fun sendChat(uid: String, text: String): SendResult = withContext(Dispatchers.IO) {
        val config = storage.readContactsConfig()
        val contact = config[uid]
            ?: return@withContext SendResult(false, null, "联系人不存在", null)
        val password = contact.pass
        val textTo = "[pass=$password]$text"
        val encrypted = crypto.encryptWithPublicPemBase64(contact.public, textTo)
        if (encrypted.isBlank()) {
            return@withContext SendResult(false, null, "加密失败", null)
        }
        val pemB64 = crypto.computePemBase64()
            ?: return@withContext SendResult(false, null, "本地公钥缺失", null)
        val (ts, sig) = crypto.signNow()
        val payload = mapOf(
            "ts" to ts,
            "sig" to sig,
            "pub" to pemB64,
            "type" to "1",
            "recipient" to uid,
            "text" to encrypted
        )
        val respResult = api.postForm(payload)
        val resp = when (respResult) {
            is ApiResult.Success -> respResult.value
            is ApiResult.Failure -> return@withContext SendResult(false, null, respResult.message, null)
        }
        val code = resp.optInt("code", -1)
        if (code == 0 && resp.has("ts")) {
            val serverTs = resp.optString("ts")
            return@withContext SendResult(true, code, null, serverTs)
        }
        return@withContext SendResult(false, code, "服务器返回错误", null)
    }

    suspend fun readChat(uid: String): ReadResult = withContext(Dispatchers.IO) {
        val config = storage.readContactsConfig()
        val contact = config[uid]
            ?: return@withContext ReadResult(false, null, "联系人不存在", 0, false)
        val password = contact.pass
        val history = storage.readChatHistory(uid)
        val lastTs = history.keys.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
        val pemB64 = crypto.computePemBase64()
            ?: return@withContext ReadResult(false, null, "本地公钥缺失", 0, false)
        val (ts, sig) = crypto.signNow()
        val payload = mapOf(
            "ts" to ts,
            "sig" to sig,
            "pub" to pemB64,
            "type" to "0",
            "from" to uid,
            "last_ts" to lastTs.toString()
        )
        val respResult = api.postForm(payload)
        val resp = when (respResult) {
            is ApiResult.Success -> respResult.value
            is ApiResult.Failure -> return@withContext ReadResult(false, null, respResult.message, 0, false)
        }
        val code = resp.optInt("code", -1)
        if (code == 0 && resp.has("data")) {
            val data = resp.optJSONObject("data") ?: JSONObject()
            var addedCount = 0
            val keys = data.keys()
            val newHistory = history.toMutableMap()
            while (keys.hasNext()) {
                val msgTs = keys.next()
                val item = data.optJSONObject(msgTs) ?: continue
                val cipherText = item.optString("text", "")
                val plain = crypto.decryptText(cipherText)
                val match = Regex("\\[pass=(.*?)\\]").find(plain)
                val pwd = match?.groupValues?.getOrNull(1) ?: ""
                val cleanText = plain.replaceFirst(Regex("\\[pass=.*?\\]"), "").trimStart()
                if (pwd != password) {
                    return@withContext ReadResult(false, code, "握手密码错误", 0, true)
                }
                newHistory[msgTs] = ChatMessage(Spokesman = 1, text = cleanText)
                addedCount += 1
            }
            if (addedCount > 0) {
                storage.writeChatHistory(uid, newHistory)
                return@withContext ReadResult(true, code, null, addedCount, false)
            }
        }
        return@withContext ReadResult(false, code, "无新消息", 0, false)
    }
}
