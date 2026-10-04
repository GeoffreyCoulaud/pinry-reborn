package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaModelMapper.toModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID

class MediaModelMapperTest {
    @Test
    fun `Given an image, Then toModel and toDomain round-trip its fields`() {
        // Given
        val media = Media(
            id = randomUUID(), pinId = randomUUID(), mimeType = "image/png",
            width = 4, height = 5, animated = false, byteSize = 6, contentHash = "h",
            storageKey = "originals/a/b/c.png", createdAt = Instant.parse("2026-07-08T00:00:00Z"),
        )
        // When
        val roundTripped = media.toModel().toDomain()
        // Then
        assertEquals(media, roundTripped)
    }

    @Test
    fun `Given an animated image, Then the flag round-trips through the model`() {
        // Given
        val media = Media(
            randomUUID(), randomUUID(), "image/gif", 10, 10, animated = true,
            byteSize = 1, contentHash = "h", storageKey = "originals/x/y/z.gif", createdAt = Instant.EPOCH,
        )
        // When
        val back = media.toModel().toDomain()
        // Then
        assertTrue(back.animated)
    }

    @Test
    fun `Given a video, Then its frames and duration round-trip through the model`() {
        // Given
        val media = Media(
            randomUUID(), randomUUID(), "video/webm", 4, 6, animated = true, byteSize = 1, contentHash = "h",
            storageKey = "originals/x/y/z.webm", createdAt = Instant.EPOCH, frames = 25,
            duration = Duration.ofMillis(1_023),
        )
        // When
        val back = media.toModel().toDomain()
        // Then
        assertEquals(25 to Duration.ofMillis(1_023), back.frames to back.duration)
    }
}
