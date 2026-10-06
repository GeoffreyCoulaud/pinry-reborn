package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaTest {
    @Test
    fun `Given image data, Then the entity exposes it and is Identifiable`() {
        // Given
        val id = randomUUID()
        val pinId = randomUUID()
        val now = Instant.parse("2026-07-08T00:00:00Z")
        // When
        val media =
            Media.StillImage(
                id = id,
                pinId = pinId,
                mimeType = "image/webp",
                width = 800,
                height = 600,
                byteSize = 12_345L,
                contentHash = "abc123",
                storageKey = "originals/u/p/$id.webp",
                createdAt = now,
            )
        // Then
        assertEquals(id, media.id)
        assertEquals(pinId, media.pinId)
        assertEquals(800, media.width)
        assertEquals("abc123", media.contentHash)
    }

    @Test
    fun `Given each kind, Then a still image alone is not animated and holds one frame`() {
        // Given
        val at = Instant.EPOCH
        val still = Media.StillImage(randomUUID(), randomUUID(), "image/png", 1, 1, 1, "h", "k", at)
        val animated = Media.AnimatedImage(randomUUID(), randomUUID(), "image/gif", 1, 1, 1, "h", "k", at, 3)
        val video =
            Media.Video(
                randomUUID(),
                randomUUID(),
                "video/mp4",
                1,
                1,
                1,
                "h",
                "k",
                at,
                25,
                Duration.ofSeconds(1),
                8,
                null,
            )
        // Then
        val kinds = listOf(still, animated, video).map { it.animated to it.frames }
        assertEquals(listOf(false to 1, true to 3, true to 25), kinds)
    }
}
