package com.kgapp.encryptionchat.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class DebugLevel { DEBUG, INFO, WARN, ERROR }

data class LogEvent(
    val timeMs: Long,
    val level: DebugLevel,
    val tag: String,
    val chatUid: String?,
    val eventName: String,
    val message: String,
    val optionalJson: String? = null
)

object DebugLog {
    private val lock = Any()
    private val events = ArrayDeque<LogEvent>()
    private val _eventsFlow = MutableStateFlow<List<LogEvent>>(emptyList())
    val eventsFlow: StateFlow<List<LogEvent>> = _eventsFlow.asStateFlow()
    private val sequence = AtomicLong(0L)

    fun append(
        context: Context,
        level: DebugLevel,
        tag: String,
        chatUid: String?,
        eventName: String,
        message: String,
        optionalJson: String? = null
    ) {
        val debugEnabled = DebugPreferences.isDebugEnabled(context)
        if (!debugEnabled && level != DebugLevel.ERROR) return
        val maxLogs = DebugPreferences.getMaxLogs(context)
        val event = LogEvent(
            timeMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            chatUid = chatUid,
            eventName = eventName,
            message = message,
            optionalJson = optionalJson
        )
        synchronized(lock) {
            events.addLast(event)
            while (events.size > maxLogs) {
                events.removeFirst()
            }
            _eventsFlow.value = events.toList()
        }
        sequence.incrementAndGet()
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
            _eventsFlow.value = emptyList()
        }
    }

    fun dumpText(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return synchronized(lock) {
            events.joinToString(separator = "\n") { event ->
                val time = formatter.format(Date(event.timeMs))
                val jsonPart = event.optionalJson?.let { " | $it" }.orEmpty()
                "$time [${event.level}] ${event.tag} ${event.eventName} uid=${event.chatUid.orEmpty()} ${event.message}$jsonPart"
            }
        }
    }

    fun exportToFile(context: Context): File {
        val filename = "debug-log-${System.currentTimeMillis()}.txt"
        val file = File(context.cacheDir, filename)
        file.writeText(dumpText(), Charsets.UTF_8)
        return file
    }

    fun summarizeSensitive(value: String, detailed: Boolean): String {
        if (value.isBlank()) return "empty"
        if (detailed) return value.take(12)
        val hash = value.hashCode().toString(16)
        return "len=${value.length}#${hash.take(8)}"
    }

    fun optionalJson(details: Map<String, Any?>, detailed: Boolean): String? {
        if (!detailed) return null
        val json = JSONObject()
        details.forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString()
    }
}
