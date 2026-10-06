package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.MediaMapper.toDto
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaMapperTest {
    @Test
    fun `Given an image, Then toDto builds a serve url relative to the origin serving it`() {
        val pinId = randomUUID()
        val media =
            Media.StillImage(randomUUID(), pinId, "image/webp", 8, 6, 99, "h", "originals/x/y/z.webp", Instant.EPOCH)
        val dto = media.toDto()
        assertEquals("/api/v1/pins/$pinId/media", dto.url)
        assertEquals("image/webp", dto.mimeType)
        assertEquals(8, dto.width)
    }
}
