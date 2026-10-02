package fr.geoffreyCoulaud.pinryReborn.api.domain.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaFormatTest {
    @Test
    fun `Given each format, Then it exposes its mime type and extension`() {
        assertEquals("image/png" to "png", MediaFormat.PNG.mimeType to MediaFormat.PNG.extension)
        assertEquals("image/jpeg" to "jpg", MediaFormat.JPEG.mimeType to MediaFormat.JPEG.extension)
        assertEquals("image/webp" to "webp", MediaFormat.WEBP.mimeType to MediaFormat.WEBP.extension)
        assertEquals("image/gif" to "gif", MediaFormat.GIF.mimeType to MediaFormat.GIF.extension)
    }
}
