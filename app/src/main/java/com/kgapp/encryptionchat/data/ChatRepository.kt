package com.kgapp.encryptionchat.data

import com.kgapp.encryptionchat.data.api.ApiResult
import com.kgapp.encryptionchat.data.api.Api4Client
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.crypto.HybridCrypto
import com.kgapp.encryptionchat.data.model.ChatMessage
import com.kgapp.encryptionchat.data.model.ContactConfig
import com.kgapp.encryptionchat.data.storage.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ChatRepository(
    private val storage: FileStorage,
    private val crypto: CryptoManager,
    private val api: Api4Client
) {
    private val chatLocks = ConcurrentHashMap<String, Mutex>()
    private val hybridCrypto = HybridCrypto(crypto)

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

    suspend fun hasPrivateKey(): Boolean = withContext(Dispatchers.IO) { crypto.hasPrivateKey() }
    suspend fun hasPublicKey(): Boolean = withContext(Dispatchers.IO) { crypto.hasPublicKey() }
    suspend fun hasKeyPair(): Boolean = withContext(Dispatchers.IO) { crypto.hasKeyPair() }
    suspend fun generateKeyPair(): Boolean = withContext(Dispatchers.IO) { crypto.generateKeyPair() }
    suspend fun getSelfName(): String? = withContext(Dispatchers.IO) { crypto.computeSelfName() }

    suspend fun getPublicPemText(): String? = withContext(Dispatchers.IO) { storage.readPublicPemText() }
    suspend fun getPrivatePemText(): String? = withContext(Dispatchers.IO) {
        storage.readPrivatePemBytes()?.toString(Charsets.UTF_8)
    }

    suspend fun importPrivatePem(pemText: String): Boolean = withContext(Dispatchers.IO) { crypto.importPrivatePem(pemText) }
    suspend fun importPublicPem(pemText: String): Boolean = withContext(Dispatchers.IO) { crypto.importPublicPem(pemText) }

    suspend fun getPemBase64(): String? = withContext(Dispatchers.IO) { crypto.computePemBase64() }

    suspend fun readContacts(): Map<String, ContactConfig> = withContext(Dispatchers.IO) { storage.readContactsConfig() }
    suspend fun getContact(uid: String): ContactConfig? = withContext(Dispatchers.IO) { storage.readContactsConfig()[uid] }

    suspend fun updateContactRemark(uid: String, remark: String): Boolean =
        withContext(Dispatchers.IO) { storage.updateContactRemark(uid, remark) }

    suspend fun updateContactShowInRecent(uid: String, show: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val config = storage.readContactsConfig()
            val existing = config[uid] ?: return@withContext false
            if (existing.showInRecent == show) return@withContext true
            config[uid] = existing.copy(showInRecent = show)
            storage.writeContactsConfig(config)
            true
        }

    suspend fun togglePinned(uid: String): Boolean =
        withContext(Dispatchers.IO) {
            val config = storage.readContactsConfig()
            val existing = config[uid] ?: return@withContext false
            config[uid] = existing.copy(pinned = !existing.pinned)
            storage.writeContactsConfig(config)
            true
        }

    suspend fun readContactsRaw(): String = withContext(Dispatchers.IO) { storage.readContactsConfigRaw() }

    suspend fun addContact(remark: String, pubKey: String, password: String): String = withContext(Dispatchers.IO) {
        val pubB64 = java.util.Base64.getEncoder().encodeToString(pubKey.toByteArray(Charsets.UTF_8))
        val uid = crypto.md5Hex(pubB64.toByteArray(Charsets.UTF_8))
        val config = storage.readContactsConfig()
        config[uid] = ContactConfig(Remark = remark, public = pubB64, pass = password)
        storage.writeContactsConfig(config)
        storage.ensureChatFile(uid)
        uid
    }

    suspend fun deleteContact(uid: String): Boolean = withContext(Dispatchers.IO) {
        val config = storage.readContactsConfig()
        if (!config.containsKey(uid)) return@withContext false
        config.remove(uid)
        storage.writeContactsConfig(config)
        storage.deleteChatHistory(uid)
        true
    }

    suspend fun readChatHistory(uid: String): Map<String, ChatMessage> =
        withContext(Dispatchers.IO) { storage.readChatHistory(uid) }

    suspend fun getLastTimestamp(uid: String): Long = withContext(Dispatchers.IO) {
        val history = storage.readChatHistory(uid)
        history.keys.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
    }

    suspend fun deleteChatHistory(uid: String) = withContext(Dispatchers.IO) {
        withChatLock(uid) {
            val ts = Instant.now().epochSecond.toString()
            val history = mapOf(ts to ChatMessage(Spokesman = 2, text = "聊天记录已清除"))
            storage.writeChatHistory(uid, history)
        }
    }

    data class RecentChat(
        val uid: String,
        val remark: String,
        val lastText: String,
        val lastTs: String,
        val pinned: Boolean
    )

    suspend fun getRecentChats(): List<RecentChat> = withContext(Dispatchers.IO) {
        val contacts = storage.readContactsConfig()
        val chatFiles = storage.listChatFiles()

        val recents = chatFiles.mapNotNull { file ->
            val uid = file.nameWithoutExtension
            val history = storage.readChatHistory(uid)
            val lastEntry = history.maxByOrNull { it.key.toLongOrNull() ?: 0L } ?: return@mapNotNull null
            val lastEpoch = lastEntry.key.toLongOrNull() ?: 0L
            if (lastEpoch <= 0L) return@mapNotNull null

            val contact = contacts[uid] ?: return@mapNotNull null
            if (!contact.showInRecent) return@mapNotNull null
            val remark = contact.Remark.ifBlank { uid }
            RecentChat(
                uid = uid,
                remark = remark,
                lastText = lastEntry.value.text,
                lastTs = lastEntry.key,
                pinned = contact.pinned
            )
        }

        recents.sortedWith(
            compareByDescending<RecentChat> { it.pinned }
                .thenByDescending { it.lastTs.toLongOrNull() ?: 0L }
        )
    }

    suspend fun appendMessage(uid: String, ts: String, speaker: Int, text: String) =
        withContext(Dispatchers.IO) {
            withChatLock(uid) {
                storage.upsertChatMessage(uid, ts, ChatMessage(Spokesman = speaker, text = text))
            }
        }

    suspend fun replaceMessageTimestamp(uid: String, oldTs: String, newTs: String, speaker: Int, text: String) =
        withContext(Dispatchers.IO) {
            withChatLock(uid) {
                storage.replaceChatTimestamp(uid, oldTs, newTs, ChatMessage(Spokesman = speaker, text = text))
            }
        }

    suspend fun deleteMessage(uid: String, ts: String): Boolean = withContext(Dispatchers.IO) {
        withChatLock(uid) {
            val history = storage.readChatHistory(uid)
            if (!history.containsKey(ts)) return@withChatLock false
            history.remove(ts)
            storage.writeChatHistory(uid, history)
            true
        }
    }

    suspend fun sendChat(uid: String, text: String): SendResult = withContext(Dispatchers.IO) {
        val config = storage.readContactsConfig()
        val contact = config[uid] ?: return@withContext SendResult(false, null, "联系人不存在", null)
        if (!contact.showInRecent) {
            config[uid] = contact.copy(showInRecent = true)
            storage.writeContactsConfig(config)
        }

        val selfUid = crypto.computeSelfName() ?: return@withContext SendResult(false, null, "本地密钥缺失", null)
        val password = contact.pass
        val textTo = "[pass=$password]$text"
        val ts = Instant.now().epochSecond
        val encrypted = hybridCrypto.encryptOutgoingPlaintext(
            fromUid = selfUid,
            toUid = uid,
            ts = ts,
            peerPublicPemBase64 = contact.public,
            plaintext = textTo
        ) ?: return@withContext SendResult(false, null, "加密失败", null)

        val respResult = api.sendMsg(
            recipient = uid,
            msg = encrypted.msg,
            key = encrypted.key
        )
        val resp = when (respResult) {
            is ApiResult.Success -> respResult.value
            is ApiResult.Failure -> return@withContext SendResult(false, null, respResult.message, null)
        }

        val code = resp.optInt("code", -1)
        if (code == 0 && resp.has("ts")) {
            val serverTs = resp.optString("ts")
            return@withContext SendResult(true, code, null, serverTs)
        }
        SendResult(false, code, "服务器返回错误", null)
    }

    suspend fun readChat(uid: String): ReadResult = withContext(Dispatchers.IO) {
        withChatLock(uid) {
            val config = storage.readContactsConfig()
            val contact = config[uid] ?: return@withChatLock ReadResult(false, null, "联系人不存在", 0, false)

            val password = contact.pass
            val selfUid = crypto.computeSelfName() ?: return@withChatLock ReadResult(false, null, "本地密钥缺失", 0, false)
            val history = storage.readChatHistory(uid)
            val lastTs = history.keys.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L

            val respResult = api.getMsg(from = uid, lastTs = lastTs)
            val resp = when (respResult) {
                is ApiResult.Success -> respResult.value
                is ApiResult.Failure -> return@withChatLock ReadResult(false, null, respResult.message, 0, false)
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
                    val key = item.optString("key", "")
                    val cipherText = item.optString("msg", "")
                    if (cipherText.isBlank()) continue

                    if (key.isBlank()) continue
                    val plain = hybridCrypto.decryptIncomingCipher(
                        fromUid = uid,
                        toUid = selfUid,
                        ts = msgTs.toLongOrNull() ?: 0L,
                        keyBase64 = key,
                        msgBase64 = cipherText
                    ) ?: continue
                    if (plain == "解密失败") continue
                    val match = Regex("\\[pass=(.*?)\\]").find(plain)
                    val pwd = match?.groupValues?.getOrNull(1) ?: ""
                    val cleanText = plain.replaceFirst(Regex("\\[pass=.*?\\]"), "").trimStart()

                    if (pwd != password) {
                        return@withChatLock ReadResult(false, code, "握手密码错误", 0, true)
                    }

                    newHistory[msgTs] = ChatMessage(Spokesman = 1, text = cleanText)
                    addedCount += 1
                }

                if (addedCount > 0) {
                    storage.writeChatHistory(uid, newHistory)
                    return@withChatLock ReadResult(true, code, null, addedCount, false)
                }
            }

            ReadResult(false, code, "无新消息", 0, false)
        }
    }

    // Incoming encrypted message handling result (single definition).
    data class IncomingResult(
        val success: Boolean,
        val message: String? = null,
        val handshakeFailed: Boolean = false
    )

    // IMPORTANT: keep lock here to avoid concurrent file write (SSE receive + send/read).
    suspend fun handleIncomingCipherMessage(
        uid: String,
        ts: Long,
        key: String,
        cipherText: String
    ): IncomingResult =
        withContext(Dispatchers.IO) {
            withChatLock(uid) {
                val config = storage.readContactsConfig()
                val contact = config[uid] ?: return@withChatLock IncomingResult(false, "联系人不存在", false)

                val history = storage.readChatHistory(uid)
                if (history.containsKey(ts.toString())) {
                    return@withChatLock IncomingResult(false, "消息已存在", false)
                }

                val password = contact.pass
                val selfUid = crypto.computeSelfName() ?: return@withChatLock IncomingResult(false, "本地密钥缺失", false)
                if (key.isBlank()) return@withChatLock IncomingResult(false, "消息解密失败", false)
                val plain = hybridCrypto.decryptIncomingCipher(
                    fromUid = uid,
                    toUid = selfUid,
                    ts = ts,
                    keyBase64 = key,
                    msgBase64 = cipherText
                )
                if (plain == null || plain == "解密失败") {
                    return@withChatLock IncomingResult(false, "消息解密失败", false)
                }

                val match = Regex("\\[pass=(.*?)\\]").find(plain)
                val pwd = match?.groupValues?.getOrNull(1) ?: ""
                val cleanText = plain.replaceFirst(Regex("\\[pass=.*?\\]"), "").trimStart()

                if (pwd != password) return@withChatLock IncomingResult(false, "握手密码错误", true)
                if (ts <= 0L || cleanText.isBlank()) return@withChatLock IncomingResult(false, "消息格式异常", false)

                storage.upsertChatMessage(uid, ts.toString(), ChatMessage(Spokesman = 1, text = cleanText))
                if (!contact.showInRecent) {
                    config[uid] = contact.copy(showInRecent = true)
                    storage.writeContactsConfig(config)
                }
                IncomingResult(true, null, false)
            }
        }

    suspend fun clearKeyPair(): Boolean = withContext(Dispatchers.IO) {
        val privateFile = storage.privateKeyFile()
        val publicFile = storage.publicKeyFile()
        val privateDeleted = privateFile.delete() || !privateFile.exists()
        val publicDeleted = publicFile.delete() || !publicFile.exists()
        privateDeleted && publicDeleted
    }

    private suspend fun <T> withChatLock(uid: String, block: suspend () -> T): T {
        val mutex = chatLocks.getOrPut(uid) { Mutex() }
        return mutex.withLock { block() }
    }
}
