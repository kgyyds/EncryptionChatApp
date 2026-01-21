package com.kgapp.encryptionchat.data.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCanonicalizerTest {
    @Test
    fun buildCanonicalDataJson_stringifiesNestedArraysAndStable() {
        val contacts = listOf(
            mapOf("uid" to "u1", "ts" to 0),
            mapOf("uid" to "u2", "ts" to 5)
        )
        val data = mapOf(
            "type" to "SseAllMsg",
            "pub" to "PUB",
            "ts" to 123,
            "contacts" to contacts
        )

        assertTrue(data["contacts"] is List<*>)
        assertTrue((data["contacts"] as List<*>).all { it is Map<*, *> })

        val canonical1 = ProtocolCanonicalizer.buildCanonicalDataJson(data)
        val canonical2 = ProtocolCanonicalizer.buildCanonicalDataJson(data)

        val expected = """{"contacts":"[\"{\\\"ts\\\":0,\\\"uid\\\":\\\"u1\\\"}\",\"{\\\"ts\\\":5,\\\"uid\\\":\\\"u2\\\"}\"]","pub":"PUB","ts":123,"type":"SseAllMsg"}"""

        assertEquals(expected, canonical1)
        assertEquals(canonical1, canonical2)
    }
}
