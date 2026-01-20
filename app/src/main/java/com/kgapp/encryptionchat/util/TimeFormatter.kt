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
    private val dateFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    fun formatTimestamp(ts: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): String {
        return formatRecentTimestamp(ts, nowEpochSeconds)
    }

    fun formatMessageTimestamp(
        ts: String,
        mode: TimeDisplayMode,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): String {
        val epochSeconds = ts.toLongOrNull() ?: return ts
        val messageDate = Date(epochSeconds * 1000L)
        return when (mode) {
            TimeDisplayMode.RELATIVE -> relativeLabel(epochSeconds, nowEpochSeconds)
            TimeDisplayMode.ABSOLUTE -> absoluteLabel(epochSeconds, messageDate, nowEpochSeconds)
            TimeDisplayMode.AUTO -> {
                if (isWithinLastWeek(epochSeconds, nowEpochSeconds)) {
                    relativeLabel(epochSeconds, nowEpochSeconds)
                } else {
                    formatDate(messageDate)
                }
            }
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

    fun formatRecentTimestamp(ts: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val epochSeconds = ts.toLongOrNull() ?: return ts
        val messageDate = Date(epochSeconds * 1000L)
        return when {
            isToday(epochSeconds, nowEpochSeconds) -> formatTime(messageDate)
            isYesterday(epochSeconds, nowEpochSeconds) -> "昨天"
            isWithinLastWeek(epochSeconds, nowEpochSeconds) -> weekDayLabel(messageDate)
            else -> formatDate(messageDate)
        }
    }

    private fun isWithinLastWeek(epochSeconds: Long, nowEpochSeconds: Long): Boolean {
        val diffSeconds = nowEpochSeconds - epochSeconds
        return diffSeconds in 0..(6 * 24 * 60 * 60)
    }

    private fun formatDate(date: Date): String = requireNotNull(dateFormatter.get()).format(date)

    private fun weekDayLabel(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }

    private fun relativeLabel(epochSeconds: Long, nowEpochSeconds: Long): String {
        val diffSeconds = nowEpochSeconds - epochSeconds
        return when {
            diffSeconds < 60 -> "刚刚"
            diffSeconds < 60 * 60 -> "${diffSeconds / 60}分钟前"
            diffSeconds < 24 * 60 * 60 -> "${diffSeconds / 3600}小时前"
            diffSeconds < 7 * 24 * 60 * 60 -> "${diffSeconds / (24 * 3600)}天前"
            else -> formatDate(Date(epochSeconds * 1000L))
        }
    }

    private fun absoluteLabel(epochSeconds: Long, messageDate: Date, nowEpochSeconds: Long): String {
        return if (isToday(epochSeconds, nowEpochSeconds)) {
            formatTime(messageDate)
        } else {
            formatDateTime(messageDate)
        }
    }
}
