package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaDownloadConfigTest {
    @Test
    fun `Given a config implementation, Then its accessors are readable`() {
        // Given
        val config =
            object : MediaDownloadConfig {
                override fun connectTimeout() = Duration.ofSeconds(5)

                override fun requestTimeout() = Duration.ofSeconds(30)

                override fun maxRedirects() = 5

                override fun allowPrivateAddresses() = false

                override fun extractionTimeout() = Duration.ofMinutes(5)
            }
        // Then
        assertEquals(Duration.ofSeconds(5), config.connectTimeout())
        assertEquals(Duration.ofSeconds(30), config.requestTimeout())
        assertEquals(5, config.maxRedirects())
        assertEquals(false, config.allowPrivateAddresses())
        assertEquals(Duration.ofMinutes(5), config.extractionTimeout())
    }
}
