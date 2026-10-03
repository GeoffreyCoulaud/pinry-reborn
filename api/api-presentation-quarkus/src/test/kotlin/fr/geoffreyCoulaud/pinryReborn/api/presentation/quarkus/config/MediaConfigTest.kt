package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaConfigTest {
    @Test
    fun `Given a config implementation, Then its accessors are readable`() {
        // Given
        val config = object : MediaConfig {
            override fun dataDir() = "/var/lib/pinry"
            override fun maxImageBytes() = 31_457_280L
            override fun maxVideoBytes() = 52_428_800L
            override fun maxVideoSeconds() = 120L
            override fun maxPixels() = 50_000_000L
        }
        // Then
        assertEquals("/var/lib/pinry", config.dataDir())
        assertEquals(31_457_280L, config.maxImageBytes())
        assertEquals(52_428_800L, config.maxVideoBytes())
        assertEquals(120L, config.maxVideoSeconds())
        assertEquals(50_000_000L, config.maxPixels())
    }
}
