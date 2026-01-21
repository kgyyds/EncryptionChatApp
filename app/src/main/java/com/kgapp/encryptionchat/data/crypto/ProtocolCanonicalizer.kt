package com.kgapp.encryptionchat.data.crypto

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

object ProtocolCanonicalizer {
    fun buildCanonicalDataJson(data: Any?): String {
        val normalized = normalizeArrayOrEmpty(data)
        val builder = StringBuilder()
        appendJsonValue(builder, normalized)
        return builder.toString()
    }

    private fun normalizeArrayOrEmpty(value: Any?): Any {
        return when (value) {
            is Map<*, *> -> normalizeMap(value)
            is JSONObject -> normalizeMap(value.toMap())
            is Iterable<*> -> normalizeList(value.toList())
            is Array<*> -> normalizeList(value.toList())
            is JSONArray -> normalizeList((0 until value.length()).map { value.opt(it) })
            else -> emptyMap<String, Any?>()
        }
    }

    private fun normalizeMap(map: Map<*, *>): Map<String, Any?> {
        val sortedEntries = map.entries
            .filter { it.key != null }
            .sortedBy { it.key.toString() }
        val normalized = LinkedHashMap<String, Any?>()
        for (entry in sortedEntries) {
            val value = entry.value
            val normalizedValue = if (isArrayLike(value)) {
                normalizeArrayToJsonString(value)
            } else {
                normalizeScalar(value)
            }
            normalized[entry.key.toString()] = normalizedValue
        }
        return normalized
    }

    private fun normalizeList(list: List<*>): List<Any?> {
        val normalized = ArrayList<Any?>(list.size)
        list.forEach { value ->
            val normalizedValue = if (isArrayLike(value)) {
                normalizeArrayToJsonString(value)
            } else {
                normalizeScalar(value)
            }
            normalized.add(normalizedValue)
        }
        return normalized
    }

    private fun normalizeScalar(value: Any?): Any? {
        return when (value) {
            null -> null
            is String, is Number, is Boolean -> value
            else -> value.toString()
        }
    }

    private fun normalizeArrayToJsonString(value: Any?): String {
        val normalized = normalizeArrayOrEmpty(value)
        val builder = StringBuilder()
        appendJsonValue(builder, normalized)
        return builder.toString()
    }

    private fun isArrayLike(value: Any?): Boolean {
        return when (value) {
            is Map<*, *>,
            is JSONObject,
            is Iterable<*>,
            is Array<*>,
            is JSONArray -> true
            else -> false
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
        val numeric = numericStringOrNull(value)
        if (numeric != null) {
            builder.append(numeric)
            return
        }
        builder.append("\"").append(escapeJsonString(value)).append("\"")
    }

    private fun numericStringOrNull(value: String): String? {
        if (value.isEmpty()) return null
        val pattern = Regex("^[+-]?(?:\\d+\\.?\\d*|\\d*\\.\\d+)(?:[eE][+-]?\\d+)?$")
        if (!pattern.matches(value)) return null
        return try {
            if (value.contains('.') || value.contains('e', true) || value.contains('E')) {
                BigDecimal(value).stripTrailingZeros().toPlainString()
            } else {
                BigDecimal(value).toBigIntegerExact().toString()
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
