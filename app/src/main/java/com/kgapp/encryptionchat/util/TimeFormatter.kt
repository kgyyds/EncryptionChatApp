package com.kgapp.encryptionchat.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun formatTimestamp(ts: String, nowEpochSeconds: Long = Instant.now().epochSecond): String {
        val epochSeconds = ts.toLongOrNull() ?: return ts
        val nowDate = LocalDate.ofInstant(Instant.ofEpochSecond(nowEpochSeconds), ZoneId.systemDefault())
        val msgDate = LocalDate.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
        return when {
            msgDate.isEqual(nowDate) -> formatTime(epochSeconds)
            msgDate.plusDays(1).isEqual(nowDate) -> "昨天 ${formatTime(epochSeconds)}"
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
