package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class MediaTest {
    @Test
    fun `Given image data, Then the entity exposes it and is Identifiable`() {
        // Given
        val id = randomUUID()
        val pinId = randomUUID()
        val now = Instant.parse("2026-07-08T00:00:00Z")
        // When
        val media = Media(
            id = id, pinId = pinId, mimeType = "image/webp",
            width = 800, height = 600, animated = false, byteSize = 12_345L,
            contentHash = "abc123", storageKey = "originals/u/p/$id.webp", createdAt = now,
        )
        // Then
        assertEquals(id, media.id)
        assertEquals(pinId, media.pinId)
        assertEquals(800, media.width)
        assertEquals("abc123", media.contentHash)
    }
}
