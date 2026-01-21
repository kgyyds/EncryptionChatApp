package com.kgapp.encryptionchat.sdk.crypto

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import java.util.SortedMap
import java.util.TreeMap

object CanonicalJson {
    fun canonicalize(value: Any?): String {
        val builder = StringBuilder()
        appendValue(builder, value)
        return builder.toString()
    }

    private fun appendValue(builder: StringBuilder, value: Any?) {
        when (value) {
            null -> builder.append("null")
            is String -> appendStringOrNumber(builder, value)
            is Number -> builder.append(value.toString())
            is Boolean -> builder.append(value.toString())
            is Map<*, *> -> appendObject(builder, normalizeMap(value))
            is JSONObject -> appendObject(builder, normalizeObject(value))
            is Iterable<*> -> appendArray(builder, value)
            is Array<*> -> appendArray(builder, value.asIterable())
            is JSONArray -> appendArray(builder, value)
            else -> appendString(builder, value.toString())
        }
    }

    private fun appendStringOrNumber(builder: StringBuilder, value: String) {
        val numeric = parseNumeric(value)
        if (numeric != null) {
            builder.append(numeric)
        } else {
            appendString(builder, value)
        }
    }

    private fun appendString(builder: StringBuilder, value: String) {
        builder.append("\"").append(escapeJsonString(value)).append("\"")
    }

    private fun appendObject(builder: StringBuilder, value: SortedMap<String, Any?>) {
        builder.append("{")
        var index = 0
        value.forEach { (key, entry) ->
            if (index > 0) builder.append(",")
            builder.append("\"").append(escapeJsonString(key)).append("\":")
            appendValue(builder, entry)
            index += 1
        }
        builder.append("}")
    }

    private fun appendArray(builder: StringBuilder, value: Iterable<*>) {
        builder.append("[")
        var index = 0
        for (entry in value) {
            if (index > 0) builder.append(",")
            appendValue(builder, entry)
            index += 1
        }
        builder.append("]")
    }

    private fun appendArray(builder: StringBuilder, value: JSONArray) {
        builder.append("[")
        for (index in 0 until value.length()) {
            if (index > 0) builder.append(",")
            appendValue(builder, value.opt(index))
        }
        builder.append("]")
    }

    private fun normalizeMap(value: Map<*, *>): SortedMap<String, Any?> {
        val sorted = TreeMap<String, Any?>()
        value.forEach { (key, entry) ->
            if (key != null) {
                sorted[key.toString()] = entry
            }
        }
        return sorted
    }

    private fun normalizeObject(value: JSONObject): SortedMap<String, Any?> {
        val sorted = TreeMap<String, Any?>()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            sorted[key] = value.opt(key)
        }
        return sorted
    }

    private fun parseNumeric(value: String): String? {
        if (value.isEmpty()) return null
        val numericRegex = Regex("[-+]?(?:\\d+\\.?\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?")
        if (!numericRegex.matches(value)) return null
        return try {
            BigDecimal(value).stripTrailingZeros().toPlainString()
        } catch (ex: NumberFormatException) {
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
