package com.kgapp.encryptionchat.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {
    @Test
    fun formatTimestamp_todayUsesTimeOnly() {
        val now = 1_700_000_000L
        val formatted = TimeFormatter.formatTimestamp(now.toString(), now)
        assertEquals(true, formatted.contains(":"))
    }

    @Test
    fun formatTimestamp_yesterdayUsesPrefix() {
        val now = 1_700_086_400L
        val yesterday = 1_700_000_000L
        val formatted = TimeFormatter.formatTimestamp(yesterday.toString(), now)
        assertEquals(true, formatted.startsWith("昨天"))
    }
}
