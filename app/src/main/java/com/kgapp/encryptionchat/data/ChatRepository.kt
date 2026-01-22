package com.kgapp.encryptionchat.data

import com.kgapp.encryptionchat.data.api.ApiResult
import com.kgapp.encryptionchat.data.api.Api4Client
import com.kgapp.encryptionchat.data.crypto.CryptoManager
import com.kgapp.encryptionchat.data.crypto.HybridCrypto
import com.kgapp.encryptionchat.data.model.ChatMessage
import com.kgapp.encryptionchat.data.model.ContactConfig
import com.kgapp.encryptionchat.data.storage.FileStorage
import com.kgapp.encryptionchat.util.DebugLevel
import com.kgapp.encryptionchat.util.DebugLog
import com.kgapp.encryptionchat.util.DebugPreferences
import com.kgapp.encryptionchat.util.UnreadCounter
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

    data class ContactUidCheck(
        val uid: String,
        val recomputedUid: String
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

    suspend fun readContacts(): Map<String, ContactConfig> = withContext(Dispatchers.IO) {
        migrateContactsIfNeeded()
        storage.readContactsConfig()
    }
    suspend fun getContact(uid: String): ContactConfig? = withContext(Dispatchers.IO) { storage.readContactsConfig()[uid] }

    suspend fun getContactUidChecks(limit: Int): List<ContactUidCheck> = withContext(Dispatchers.IO) {
        migrateContactsIfNeeded()
        val config = storage.readContactsConfig()
        config.entries.take(limit).mapNotNull { (uid, contact) ->
            val canonicalPubB64 = runCatching { crypto.canonicalizePubBase64(contact.public) }.getOrNull()
                ?: return@mapNotNull null
            val recomputed = crypto.computeUidFromPubBase64(canonicalPubB64)
            ContactUidCheck(uid = uid, recomputedUid = recomputed)
        }
    }

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
        val pubB64 = crypto.pemToCanonicalPubBase64(pubKey)
        val uid = crypto.computeUidFromPubBase64(pubB64)
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

    suspend fun sendChat(
        uid: String,
        text: String,
        onRetry: (suspend (attempt: Int) -> Unit)? = null
    ): SendResult = withContext(Dispatchers.IO) {
        migrateContactsIfNeeded()
        val config = storage.readContactsConfig()
        val contact = config[uid] ?: return@withContext SendResult(false, null, "联系人不存在", null)
        if (!contact.showInRecent) {
            config[uid] = contact.copy(showInRecent = true)
            storage.writeContactsConfig(config)
        }

        val selfPubB64 = crypto.computePemBase64() ?: return@withContext SendResult(false, null, "本地密钥缺失", null)
        val selfUid = crypto.computeUidFromPubBase64(selfPubB64)
        var contactUid = uid
        var contactConfig = contact
        val canonicalRecipientPub = crypto.canonicalizePubBase64(contact.public)
        val recomputedRecipientUid = crypto.computeUidFromPubBase64(canonicalRecipientPub)
        if (canonicalRecipientPub != contact.public || recomputedRecipientUid != uid) {
            DebugLog.append(
                context = storage.appContext,
                level = DebugLevel.ERROR,
                tag = "CONTACT",
                chatUid = uid,
                eventName = "uid_mismatch",
                message = "CONTACT_UID_MISMATCH oldUid=$uid recomputedUid=$recomputedRecipientUid"
            )
            val repaired = repairContactUid(uid, recomputedRecipientUid, canonicalRecipientPub, contact)
            if (!repaired) {
                return@withContext SendResult(false, null, "联系人UID异常", null)
            }
            contactUid = recomputedRecipientUid
            contactConfig = contact.copy(public = canonicalRecipientPub)
        }
        val password = contactConfig.pass
        val textTo = "[pass=$password] $text"
        val ts = Instant.now().epochSecond
        val detailed = DebugPreferences.isDetailedLogs(storage.appContext)
        DebugLog.append(
            context = storage.appContext,
            level = DebugLevel.INFO,
            tag = "CRYPTO",
            chatUid = contactUid,
            eventName = "encrypt_outgoing",
            message = "fromUid=$selfUid toUid=$contactUid ts=$ts keyBlobVersion=${hybridCrypto.keyBlobVersion} plaintextLen=${textTo.length}",
            optionalJson = DebugLog.optionalJson(
                mapOf(
                    "fromUid" to selfUid,
                    "toUid" to contactUid,
                    "ts" to ts,
                    "keyBlobVersion" to hybridCrypto.keyBlobVersion,
                    "plaintextLen" to textTo.length
                ),
                detailed
            )
        )
        val encrypted = hybridCrypto.encryptOutgoingPlaintext(
            fromUid = selfUid,
            toUid = contactUid,
            ts = ts,
            peerPublicPemBase64 = contactConfig.public,
            plaintext = textTo
        ) ?: return@withContext SendResult(false, null, "加密失败", null)
        val pubHashPrefix = crypto.md5Hex(contactConfig.public.toByteArray(Charsets.UTF_8)).take(8)
        DebugLog.append(
            context = storage.appContext,
            level = DebugLevel.INFO,
            tag = "SEND",
            chatUid = contactUid,
            eventName = "send_msg",
            message = "selfPubLen=${selfPubB64.length} selfPubEndsWithNewline=${selfPubB64.endsWith("\n")} " +
                "selfUid=$selfUid recipientUid=$contactUid recipientPubLen=${contactConfig.public.length} " +
                "recipientPubEndsWithNewline=${contactConfig.public.endsWith("\n")} recomputedRecipientUid=$recomputedRecipientUid " +
                "pubMd5Prefix=$pubHashPrefix msgLen=${encrypted.msg.length} keyLen=${encrypted.key.length}"
        )

        val resp = retryWithBackoff(maxAttempts = 3, onRetry = onRetry) { attempt ->
            val respResult = api.sendMsg(
                recipient = contactUid,
                msg = encrypted.msg,
                key = encrypted.key
            )
            when (respResult) {
                is ApiResult.Success -> respResult.value
                is ApiResult.Failure -> {
                    if (attempt < 3) null else return@retryWithBackoff null
                }
            }
        }
            ?: return@withContext SendResult(false, null, "消息发送失败", null)

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

            val resp = retryWithBackoff(maxAttempts = 3, onRetry = null) { attempt ->
                val respResult = api.getMsg(from = uid, lastTs = lastTs)
                when (respResult) {
                    is ApiResult.Success -> respResult.value
                    is ApiResult.Failure -> if (attempt < 3) null else null
                }
            } ?: return@withChatLock ReadResult(false, null, "拉取消息失败", 0, false)

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
                    val decryptResult = hybridCrypto.decryptIncomingCipher(
                        fromUid = uid,
                        toUid = selfUid,
                        ts = msgTs.toLongOrNull() ?: 0L,
                        keyBase64 = key,
                        msgBase64 = cipherText
                    )
                    val plain = decryptResult.plaintext
                    if (plain == null) {
                        val detailed = DebugPreferences.isDetailedLogs(storage.appContext)
                        val error = decryptResult.throwable
                        val reason = decryptResult.errorReason
                        DebugLog.append(
                            context = storage.appContext,
                            level = DebugLevel.ERROR,
                            tag = "CRYPTO",
                            chatUid = uid,
                            eventName = "decrypt_failed",
                            message = "fromUid=$uid toUid=$selfUid serverTs=$msgTs keyBlobVersion=${decryptResult.keyBlobVersion} reason=${reason.orEmpty()}",
                            optionalJson = DebugLog.optionalJson(
                                mapOf(
                                    "fromUid" to uid,
                                    "toUid" to selfUid,
                                    "serverTs" to msgTs,
                                    "keyBlobVersion" to decryptResult.keyBlobVersion,
                                    "reason" to reason,
                                    "exception" to error?.javaClass?.simpleName,
                                    "exceptionMessage" to error?.message
                                ),
                                detailed
                            )
                        )
                        continue
                    }
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
                val detailed = DebugPreferences.isDetailedLogs(storage.appContext)
                DebugLog.append(
                    context = storage.appContext,
                    level = DebugLevel.INFO,
                    tag = "CRYPTO",
                    chatUid = uid,
                    eventName = "incoming",
                    message = "ts=$ts keyLen=${key.length} msgLen=${cipherText.length}",
                    optionalJson = DebugLog.optionalJson(
                        mapOf(
                            "keySummary" to DebugLog.summarizeSensitive(key, detailed),
                            "msgSummary" to DebugLog.summarizeSensitive(cipherText, detailed)
                        ),
                        detailed
                    )
                )
                val config = storage.readContactsConfig()
                val contact = config[uid] ?: return@withChatLock IncomingResult(false, "联系人不存在", false)

                val history = storage.readChatHistory(uid)
                if (history.containsKey(ts.toString())) {
                    DebugLog.append(
                        context = storage.appContext,
                        level = DebugLevel.WARN,
                        tag = "STORE",
                        chatUid = uid,
                        eventName = "duplicate",
                        message = "ts=$ts"
                    )
                    return@withChatLock IncomingResult(false, "消息已存在", false)
                }

                val password = contact.pass
                val selfUid = crypto.computeSelfName() ?: return@withChatLock IncomingResult(false, "本地密钥缺失", false)
                if (key.isBlank()) return@withChatLock IncomingResult(false, "消息解密失败", false)
                val decryptResult = hybridCrypto.decryptIncomingCipher(
                    fromUid = uid,
                    toUid = selfUid,
                    ts = ts,
                    keyBase64 = key,
                    msgBase64 = cipherText
                )
                val plain = decryptResult.plaintext
                if (plain == null || plain == "解密失败") {
                    val error = decryptResult.throwable
                    val reason = decryptResult.errorReason
                    DebugLog.append(
                        context = storage.appContext,
                        level = DebugLevel.ERROR,
                        tag = "CRYPTO",
                        chatUid = uid,
                        eventName = "decrypt_failed",
                        message = "fromUid=$uid toUid=$selfUid serverTs=$ts keyBlobVersion=${decryptResult.keyBlobVersion} reason=${reason.orEmpty()}",
                        optionalJson = DebugLog.optionalJson(
                            mapOf(
                                "fromUid" to uid,
                                "toUid" to selfUid,
                                "serverTs" to ts,
                                "keyBlobVersion" to decryptResult.keyBlobVersion,
                                "reason" to reason,
                                "exception" to error?.javaClass?.simpleName,
                                "exceptionMessage" to error?.message
                            ),
                            detailed
                        )
                    )
                    return@withChatLock IncomingResult(false, "消息解密失败", false)
                }

                val match = Regex("\\[pass=(.*?)\\]").find(plain)
                val pwd = match?.groupValues?.getOrNull(1) ?: ""
                val cleanText = plain.replaceFirst(Regex("\\[pass=.*?\\]"), "").trimStart()

                if (pwd != password) return@withChatLock IncomingResult(false, "握手密码错误", true)
                if (ts <= 0L || cleanText.isBlank()) return@withChatLock IncomingResult(false, "消息格式异常", false)

                storage.upsertChatMessage(uid, ts.toString(), ChatMessage(Spokesman = 1, text = cleanText))
                DebugLog.append(
                    context = storage.appContext,
                    level = DebugLevel.INFO,
                    tag = "STORE",
                    chatUid = uid,
                    eventName = "stored",
                    message = "ts=$ts"
                )
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

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int,
        onRetry: (suspend (attempt: Int) -> Unit)?,
        block: suspend (attempt: Int) -> T?
    ): T? {
        var attempt = 1
        var backoffMs = 1000L
        while (attempt <= maxAttempts) {
            val result = block(attempt)
            if (result != null) return result
            if (attempt >= maxAttempts) break
            onRetry?.invoke(attempt + 1)
            kotlinx.coroutines.delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(8000L)
            attempt += 1
        }
        return null
    }

    private fun migrateContactsIfNeeded() {
        val prefs = storage.appContext.getSharedPreferences(CONTACT_MIGRATION_PREFS, android.content.Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(CONTACT_MIGRATION_KEY, 0)
        if (currentVersion >= CONTACT_MIGRATION_VERSION) return
        val config = storage.readContactsConfig()
        if (config.isEmpty()) {
            prefs.edit().putInt(CONTACT_MIGRATION_KEY, CONTACT_MIGRATION_VERSION).apply()
            return
        }
        var changed = false
        val updated = config.toMutableMap()
        val migrations = mutableListOf<Pair<String, String>>()
        config.forEach { (uid, contact) ->
            val canonicalPubB64 = try {
                crypto.canonicalizePubBase64(contact.public)
            } catch (ex: Exception) {
                return@forEach
            }
            val recomputed = crypto.computeUidFromPubBase64(canonicalPubB64)
            if (recomputed == uid) {
                if (canonicalPubB64 != contact.public) {
                    updated[uid] = contact.copy(public = canonicalPubB64)
                    changed = true
                }
                return@forEach
            }
            if (!updated.containsKey(recomputed)) {
                updated.remove(uid)
                updated[recomputed] = contact.copy(public = canonicalPubB64, legacyUid = uid)
                migrations.add(uid to recomputed)
                changed = true
            } else {
                updated[uid] = contact.copy(public = canonicalPubB64, legacyUid = uid)
                changed = true
            }
        }
        if (changed) {
            storage.writeContactsConfig(updated)
            migrations.forEach { (oldUid, newUid) ->
                storage.ensureChatFile(newUid)
                moveChatHistory(oldUid, newUid)
                UnreadCounter.migrateUids(storage.appContext, mapOf(oldUid to newUid))
            }
        }
        prefs.edit().putInt(CONTACT_MIGRATION_KEY, CONTACT_MIGRATION_VERSION).apply()
    }

    private fun repairContactUid(
        oldUid: String,
        newUid: String,
        canonicalPubB64: String,
        contact: ContactConfig
    ): Boolean {
        val config = storage.readContactsConfig()
        val existing = config[oldUid] ?: return false
        if (newUid == oldUid) {
            if (existing.public != canonicalPubB64) {
                config[oldUid] = existing.copy(public = canonicalPubB64)
                storage.writeContactsConfig(config)
            }
            return true
        }
        if (config.containsKey(newUid)) {
            val merged = config[newUid]?.copy(public = canonicalPubB64) ?: contact.copy(public = canonicalPubB64)
            config[newUid] = merged
            config.remove(oldUid)
        } else {
            config.remove(oldUid)
            config[newUid] = contact.copy(public = canonicalPubB64, legacyUid = oldUid)
        }
        storage.writeContactsConfig(config)
        storage.ensureChatFile(newUid)
        moveChatHistory(oldUid, newUid)
        UnreadCounter.migrateUids(storage.appContext, mapOf(oldUid to newUid))
        return true
    }

    private fun moveChatHistory(oldUid: String, newUid: String) {
        val oldFile = storage.chatFile(oldUid)
        if (!oldFile.exists()) return
        if (storage.renameChatHistory(oldUid, newUid)) return
        val oldHistory = storage.readChatHistory(oldUid)
        val newHistory = storage.readChatHistory(newUid).toMutableMap()
        oldHistory.forEach { (ts, message) ->
            if (!newHistory.containsKey(ts)) {
                newHistory[ts] = message
            }
        }
        storage.writeChatHistory(newUid, newHistory)
        storage.deleteChatHistory(oldUid)
    }

    companion object {
        private const val CONTACT_MIGRATION_PREFS = "contact_migration_prefs"
        private const val CONTACT_MIGRATION_KEY = "contact_uid_migration_version"
        private const val CONTACT_MIGRATION_VERSION = 1
    }
}
