package com.kgapp.encryptionchat.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun formatTimestamp(ts: String, nowEpochSeconds: Long = Instant.now().epochSecond): String {
        val epochSeconds = ts.toLongOrNull() ?: return ts
        val diff = nowEpochSeconds - epochSeconds
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60} 分钟前"
            diff < 86400 -> "${diff / 3600} 小时前"
            diff < 172800 -> "昨天 ${formatTime(epochSeconds)}"
            else -> formatDateTime(epochSeconds)
        }
    }

    private fun formatTime(epochSeconds: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
        return dateTime.format(timeFormatter)
    }

    private fun formatDateTime(epochSeconds: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
        return dateTime.format(dateTimeFormatter)
    }
}
