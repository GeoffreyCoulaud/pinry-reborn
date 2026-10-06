package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaModelMapper.toModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID

class MediaModelMapperTest {
    private fun video(sound: Media.Sound?) =
        Media.Video(
            randomUUID(), randomUUID(), "video/webm", 4, 6, 1, "h", "originals/x/y/z.webm", Instant.EPOCH, 25,
            Duration.ofMillis(1_023), videoBitRate = 80_000, sound = sound,
        )

    @Test
    fun `Given a still image, Then toModel and toDomain round-trip its fields`() {
        // Given
        val media = Media.StillImage(
            id = randomUUID(), pinId = randomUUID(), mimeType = "image/png",
            width = 4, height = 5, byteSize = 6, contentHash = "h",
            storageKey = "originals/a/b/c.png", createdAt = Instant.parse("2026-07-08T00:00:00Z"),
        )
        // When
        val roundTripped = media.toModel().toDomain()
        // Then
        assertEquals(media, roundTripped)
    }

    @Test
    fun `Given an animated image, Then it round-trips as one, with its frames`() {
        // Given
        val media = Media.AnimatedImage(
            randomUUID(), randomUUID(), "image/gif", 10, 10, 1, "h", "originals/x/y/z.gif", Instant.EPOCH, frames = 3,
        )
        // When
        val back = media.toModel().toDomain()
        // Then
        assertEquals(media, back)
    }

    @Test
    fun `Given a video with sound, Then its frames, duration, rates and channels round-trip through the model`() {
        val media = video(Media.Sound(2, 64_000))
        assertEquals(media, media.toModel().toDomain())
    }

    @Test
    fun `Given a video without sound, Then it round-trips with no sound`() {
        val media = video(sound = null)
        assertEquals(media, media.toModel().toDomain())
    }

    @Test
    fun `Given a video row missing its duration, its video rate or its audio rate, Then reading it fails`() {
        val stored = { video(Media.Sound(2, 64_000)).toModel() }
        val rows = listOf(
            stored().apply { durationMillis = null },
            stored().apply { videoBitRate = null },
            stored().apply { audioBitRate = null },
        )
        rows.forEach { row -> assertThrows(IllegalStateException::class.java) { row.toDomain() } }
    }
}
