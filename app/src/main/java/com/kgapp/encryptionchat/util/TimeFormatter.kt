package com.kgapp.encryptionchat.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val timeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    private val dateTimeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    fun formatTimestamp(ts: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val epochSeconds = ts.toLongOrNull() ?: return ts
        val messageDate = Date(epochSeconds * 1000L)
        return when {
            isToday(epochSeconds, nowEpochSeconds) -> formatTime(messageDate)
            isYesterday(epochSeconds, nowEpochSeconds) -> "昨天 ${formatTime(messageDate)}"
            else -> formatDateTime(messageDate)
        }
    }

    private fun isToday(epochSeconds: Long, nowEpochSeconds: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowEpochSeconds * 1000L
        val nowYear = calendar.get(Calendar.YEAR)
        val nowDay = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.timeInMillis = epochSeconds * 1000L
        return calendar.get(Calendar.YEAR) == nowYear && calendar.get(Calendar.DAY_OF_YEAR) == nowDay
    }

    private fun isYesterday(epochSeconds: Long, nowEpochSeconds: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowEpochSeconds * 1000L
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayYear = calendar.get(Calendar.YEAR)
        val yesterdayDay = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.timeInMillis = epochSeconds * 1000L
        return calendar.get(Calendar.YEAR) == yesterdayYear && calendar.get(Calendar.DAY_OF_YEAR) == yesterdayDay
    }

    private fun formatTime(date: Date): String = requireNotNull(timeFormatter.get()).format(date)

    private fun formatDateTime(date: Date): String = requireNotNull(dateTimeFormatter.get()).format(date)
}
