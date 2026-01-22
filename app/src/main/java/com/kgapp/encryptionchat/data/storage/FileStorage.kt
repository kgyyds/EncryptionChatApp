package com.kgapp.encryptionchat.data.storage

import android.content.Context
import com.kgapp.encryptionchat.data.model.ChatMessage
import com.kgapp.encryptionchat.data.model.ContactConfig
import com.kgapp.encryptionchat.util.DebugLevel
import com.kgapp.encryptionchat.util.DebugLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class FileStorage(private val context: Context) {
    val appContext: Context get() = context
    private val jsonPretty2 = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }
    private val jsonPretty4 = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
        encodeDefaults = true
    }
    private val jsonCompact = Json { encodeDefaults = true }

    val baseDir: File get() = context.filesDir

    private fun resolve(relative: String): File = File(baseDir, relative)

    fun ensureKeyDirs(): File {
        val dir = resolve("config/key")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun privateKeyFile(): File = resolve("config/key/private.pem")

    fun publicKeyFile(): File = resolve("config/key/public.pem")

    fun contactsConfigFile(): File = resolve("contacts/config.json")

    fun readContactsConfigRaw(): String {
        val file = contactsConfigFile()
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("{}", Charsets.UTF_8)
        }
        return file.readText(Charsets.UTF_8)
    }

    fun chatFile(uid: String): File = resolve("contacts/chats/$uid.json")

    fun listChatFiles(): List<File> {
        val dir = resolve("contacts/chats")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.extension == "json" }?.toList().orEmpty()
    }

    fun deleteChatHistory(uid: String) {
        val file = chatFile(uid)
        if (file.exists()) {
            file.delete()
        }
    }

    fun renameChatHistory(oldUid: String, newUid: String): Boolean {
        val oldFile = chatFile(oldUid)
        if (!oldFile.exists()) return false
        val newFile = chatFile(newUid)
        ensureChatFile(newUid)
        if (newFile.exists()) {
            return false
        }
        oldFile.parentFile?.mkdirs()
        return oldFile.renameTo(newFile)
    }

    fun readContactsConfig(): MutableMap<String, ContactConfig> {
        val file = contactsConfigFile()
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("{}", Charsets.UTF_8)
            return mutableMapOf()
        }
        val content = file.readText(Charsets.UTF_8)
        if (content.isBlank()) {
            return mutableMapOf()
        }
        return runCatching {
            jsonCompact.decodeFromString<Map<String, ContactConfig>>(content).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun writeContactsConfig(config: Map<String, ContactConfig>) {
        val file = contactsConfigFile()
        file.parentFile?.mkdirs()
        val content = jsonPretty4.encodeToString(config)
        file.writeText(content, Charsets.UTF_8)
    }

    fun updateContactRemark(uid: String, remark: String): Boolean {
        val config = readContactsConfig()
        val existing = config[uid] ?: return false
        config[uid] = existing.copy(Remark = remark)
        writeContactsConfig(config)
        return true
    }

    fun ensureChatFile(uid: String) {
        val file = chatFile(uid)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val defaultHistory = mapOf("0" to ChatMessage(Spokesman = 0, text = "暂无记录"))
            val content = jsonPretty2.encodeToString(defaultHistory)
            file.writeText(content, Charsets.UTF_8)
        }
    }

    fun readChatHistory(uid: String): MutableMap<String, ChatMessage> {
        ensureChatFile(uid)
        val file = chatFile(uid)
        val content = file.readText(Charsets.UTF_8)
        if (content.isBlank()) {
            return mutableMapOf()
        }
        return runCatching {
            jsonCompact.decodeFromString<Map<String, ChatMessage>>(content).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun writeChatHistory(uid: String, history: Map<String, ChatMessage>) {
        val file = chatFile(uid)
        val beforeSize = if (file.exists()) readChatHistory(uid).size else 0
        file.parentFile?.mkdirs()
        val content = jsonPretty2.encodeToString(history)
        file.writeText(content, Charsets.UTF_8)
        DebugLog.append(
            context = appContext,
            level = DebugLevel.INFO,
            tag = "STORE",
            chatUid = uid,
            eventName = "write_history",
            message = "before=$beforeSize after=${history.size}"
        )
    }

    fun upsertChatMessage(uid: String, ts: String, message: ChatMessage) {
        val history = readChatHistory(uid)
        val existed = history.containsKey(ts)
        history[ts] = message
        writeChatHistory(uid, history)
        DebugLog.append(
            context = appContext,
            level = DebugLevel.INFO,
            tag = "STORE",
            chatUid = uid,
            eventName = "upsert",
            message = "ts=$ts existed=$existed size=${history.size}"
        )
    }

    fun replaceChatTimestamp(uid: String, oldTs: String, newTs: String, message: ChatMessage) {
        val history = readChatHistory(uid)
        history.remove(oldTs)
        history[newTs] = message
        writeChatHistory(uid, history)
        DebugLog.append(
            context = appContext,
            level = DebugLevel.INFO,
            tag = "STORE",
            chatUid = uid,
            eventName = "replace_ts",
            message = "old=$oldTs new=$newTs size=${history.size}"
        )
    }

    fun readPublicPemText(): String? {
        val file = publicKeyFile()
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun readPrivatePemBytes(): ByteArray? {
        val file = privateKeyFile()
        return if (file.exists()) file.readBytes() else null
    }

    fun wipeSensitiveData() {
        privateKeyFile().delete()
        publicKeyFile().delete()
        contactsConfigFile().delete()
        val chatsDir = resolve("contacts/chats")
        if (chatsDir.exists()) {
            chatsDir.deleteRecursively()
        }
    }
}
