package com.kgapp.encryptionchat.data.crypto

import android.util.Log
import com.kgapp.encryptionchat.BuildConfig
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

object ProtocolCanonicalizer {
    fun buildCanonicalDataJson(data: Any?): String {
        val normalized = normalizeStructure(data) ?: return "{}"
        val builder = StringBuilder()
        appendJsonValue(builder, normalized)
        return builder.toString()
    }

    fun canonicalStringForSigning(data: Map<String, Any?>): String {
        val canonical = buildCanonicalDataJson(data)
        if (BuildConfig.DEBUG) {
            val type = data["type"]?.toString().orEmpty()
            if (type == "SseAllMsg") {
                val contacts = data["contacts"]
                val contactsIsArray = contacts is Iterable<*> || contacts is Array<*> || contacts is JSONArray
                Log.d(
                    "ProtocolCanonicalizer",
                    "SseAllMsg canonical=${canonical.take(200)} contactsIsArray=$contactsIsArray"
                )
            }
        }
        return canonical
    }

    private fun normalizeStructure(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> normalizeMap(value)
            is JSONObject -> normalizeMap(jsonObjectToMap(value))
            is Iterable<*> -> normalizeList(value.toList())
            is Array<*> -> normalizeList(value.toList())
            is JSONArray -> normalizeList((0 until value.length()).map { value.opt(it) })
            else -> normalizeScalar(value)
        }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.opt(key)
        }
        return map
    }

    private fun normalizeMap(map: Map<*, *>): Map<String, Any?> {
        val sortedEntries = map.entries
            .filter { it.key != null }
            .sortedBy { it.key.toString() }
        val normalized = LinkedHashMap<String, Any?>()
        for (entry in sortedEntries) {
            normalized[entry.key.toString()] = normalizeStructure(entry.value)
        }
        return normalized
    }

    private fun normalizeList(list: List<*>): List<Any?> {
        val normalized = ArrayList<Any?>(list.size)
        list.forEach { value ->
            normalized.add(normalizeStructure(value))
        }
        return normalized
    }

    private fun normalizeScalar(value: Any?): Any? {
        return when (value) {
            null -> null
            is String -> numericStringToNumber(value) ?: value
            is Number, is Boolean -> value
            else -> value.toString()
        }
    }

    private fun appendJsonValue(builder: StringBuilder, value: Any?) {
        when (value) {
            null -> builder.append("null")
            is String -> appendJsonString(builder, value)
            is Number, is Boolean -> builder.append(value.toString())
            is Map<*, *> -> appendJsonObject(builder, value)
            is Iterable<*> -> appendJsonArray(builder, value)
            else -> appendJsonString(builder, value.toString())
        }
    }

    private fun appendJsonObject(builder: StringBuilder, value: Map<*, *>) {
        builder.append("{")
        value.entries.forEachIndexed { index, entry ->
            if (index > 0) builder.append(",")
            builder.append("\"").append(escapeJsonString(entry.key.toString())).append("\":")
            appendJsonValue(builder, entry.value)
        }
        builder.append("}")
    }

    private fun appendJsonArray(builder: StringBuilder, value: Iterable<*>) {
        builder.append("[")
        val iterator = value.iterator()
        var index = 0
        while (iterator.hasNext()) {
            if (index > 0) builder.append(",")
            appendJsonValue(builder, iterator.next())
            index += 1
        }
        builder.append("]")
    }

    private fun appendJsonString(builder: StringBuilder, value: String) {
        builder.append("\"").append(escapeJsonString(value)).append("\"")
    }

    private fun numericStringToNumber(value: String): Number? {
        if (value.isEmpty()) return null
        val pattern = Regex("^[+-]?(?:\\d+\\.?\\d*|\\d*\\.\\d+)(?:[eE][+-]?\\d+)?$")
        if (!pattern.matches(value)) return null
        return try {
            if (value.contains('.') || value.contains('e', true) || value.contains('E')) {
                BigDecimal(value).stripTrailingZeros()
            } else {
                BigDecimal(value).toBigIntegerExact()
            }
        } catch (ex: Exception) {
            null
        }
    }

    private fun escapeJsonString(value: String): String {
        val escaped = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '\\' -> escaped.append("\\\\")
                '"' -> escaped.append("\\\"")
                '\b' -> escaped.append("\\b")
                '\u000C' -> escaped.append("\\f")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                else -> {
                    if (ch < ' ') {
                        escaped.append(String.format("\\u%04x", ch.code))
                    } else {
                        escaped.append(ch)
                    }
                }
            }
        }
        return escaped.toString()
    }
}
