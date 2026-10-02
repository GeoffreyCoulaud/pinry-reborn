package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTooManyPixelsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class VipsImageProbeTest {
    private val probe = VipsImageProbe()

    private fun staged(name: String) =
        StagedFile(path = Path.of("src/test/resources/fixtures", name).toString(), byteSize = 0, contentHash = "")

    @Test
    fun `Given a PNG, Then probe returns PNG with its dimensions`() {
        val result = probe.probe(staged("sample.png"), maxPixels = 1_000_000)
        assertEquals(MediaFormat.PNG, result.format)
        assertEquals(10, result.width)
        assertEquals(10, result.height)
    }

    @Test
    fun `Given a JPEG, Then probe returns JPEG`() {
        assertEquals(MediaFormat.JPEG, probe.probe(staged("sample.jpg"), 1_000_000).format)
    }

    @Test
    fun `Given a WebP, Then probe returns WEBP`() {
        assertEquals(MediaFormat.WEBP, probe.probe(staged("sample.webp"), 1_000_000).format)
    }

    @Test
    fun `Given an animated WebP, Then probe accepts it as WEBP`() {
        assertEquals(MediaFormat.WEBP, probe.probe(staged("animated.webp"), 1_000_000).format)
    }

    @Test
    fun `Given an animated GIF, Then probe accepts it as GIF`() {
        assertEquals(MediaFormat.GIF, probe.probe(staged("animated.gif"), 1_000_000).format)
    }

    @Test
    fun `Given a non-image, Then probe throws UndecodableImageException`() {
        assertThrows(UndecodableImageException::class.java) {
            probe.probe(staged("not-an-image.txt"), 1_000_000)
        }
    }

    @Test
    fun `Given an image over the pixel limit, Then probe throws ImageTooManyPixelsException`() {
        assertThrows(ImageTooManyPixelsException::class.java) {
            probe.probe(staged("sample.png"), maxPixels = 1)
        }
    }

    @Test
    fun `Given a TIFF, Then probe throws UnsupportedImageFormatException`() {
        assertThrows(UnsupportedImageFormatException::class.java) {
            probe.probe(staged("sample.tiff"), 1_000_000)
        }
    }

    @Test
    fun `Given a static PNG, Then probe reports animated = false`() {
        // Given / When
        val result = probe.probe(staged("sample.png"), maxPixels = 1_000_000)
        // Then
        assertFalse(result.animated)
    }

    @Test
    fun `Given an animated GIF, Then probe reports animated = true`() {
        // Given / When
        val result = probe.probe(staged("animated.gif"), maxPixels = 1_000_000)
        // Then
        assertTrue(result.animated)
    }

    @Test
    fun `Given an animated WebP, Then probe reports animated = true`() {
        // Given / When
        val result = probe.probe(staged("animated.webp"), maxPixels = 1_000_000)
        // Then
        assertTrue(result.animated)
    }

    @Test
    fun `Given a static WebP, Then probe reports animated = false`() {
        // Given / When
        val result = probe.probe(staged("sample.webp"), maxPixels = 1_000_000)
        // Then
        assertFalse(result.animated)
    }
}
