package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Runs the `vips` and `vipsheader` on the `PATH`. */
class VipsImageTransformerTest {
    private val transformer = VipsImageTransformer(quality = 80, Duration.ofSeconds(60), DECODER_MEMORY)
    private val probe = VipsImageProbe(Duration.ofSeconds(60), DECODER_MEMORY)

    private fun fixture(name: String) = Path.of("src/test/resources/fixtures", name)

    private fun renderAndProbe(fixture: String, spec: RenditionSpec): ProbeResult {
        val staged = Files.newInputStream(fixture(fixture)).use { transformer.render(it, spec) }
        return try {
            probe.probe(StagedFile(staged.path, 0, ""))
        } finally {
            Files.deleteIfExists(Path.of(staged.path))
        }
    }

    private fun square(shortestSide: Int, animated: Boolean) =
        RenditionSpec(shortestSide, animated, frameWidth = 10, frameHeight = 10)

    @Test
    fun `Given a static image and a smaller size, Then it downscales to WebP with that shortest side`() {
        val result = renderAndProbe("sample.png", square(shortestSide = 4, animated = false))
        assertEquals(MediaFormat.WEBP, result.format)
        assertEquals(4, minOf(result.width, result.height))
        assertFalse(result.animated)
    }

    @Test
    fun `Given a size equal to the native shortest side, Then it re-encodes WebP without upscaling`() {
        val result = renderAndProbe("sample.png", square(shortestSide = 10, animated = false))
        assertEquals(MediaFormat.WEBP, result.format)
        assertEquals(10, minOf(result.width, result.height))
    }

    @Test
    fun `Given an image smaller than the size asked, Then it is rendered at its own size`() {
        val result = renderAndProbe("sample.png", square(shortestSide = 20, animated = false))
        assertEquals(10, result.width)
        assertEquals(10, result.height)
    }

    @Test
    fun `Given an animated source with animated = true, Then it downscales and keeps the animation`() {
        val result = renderAndProbe("animated.gif", square(shortestSide = 4, animated = true))
        assertEquals(MediaFormat.WEBP, result.format)
        assertEquals(4, minOf(result.width, result.height))
        assertTrue(result.animated)
    }

    @Test
    fun `Given an animated source with animated = false, Then it flattens to a static WebP`() {
        val result = renderAndProbe("animated.gif", square(shortestSide = 4, animated = false))
        assertEquals(MediaFormat.WEBP, result.format)
        assertEquals(4, minOf(result.width, result.height))
        assertFalse(result.animated)
    }

    @Test
    fun `Given a landscape JPEG whose EXIF orientation turns it upright, Then it is rendered unrotated`() {
        // Given: 20x10 as stored, oriented 6, which turned upright would be 10x20
        val spec = RenditionSpec(shortestSide = 5, animated = false, frameWidth = 20, frameHeight = 10)
        // When
        val result = renderAndProbe("oriented.jpg", spec)
        // Then
        assertEquals(10, result.width)
        assertEquals(5, result.height)
    }

    @Test
    fun `Given a file vips refuses, Then the render is reported undecodable`() {
        assertThrows(UndecodableImageException::class.java) {
            Files.newInputStream(fixture("not-an-image.txt")).use { transformer.render(it, square(4, false)) }
        }
    }

    @Test
    fun `Given a timeout vips cannot meet, Then the process is destroyed and the render reported undecodable`() {
        // Given
        val impatient = VipsImageTransformer(quality = 80, Duration.ZERO, DECODER_MEMORY)
        // When
        assertThrows(UndecodableImageException::class.java) {
            Files.newInputStream(fixture("sample.png")).use { impatient.render(it, square(4, false)) }
        }
        // Then
        assertEquals(0, ProcessHandle.current().children().count())
    }

    @Test
    fun `Given an address space vips cannot start in, Then the render is reported undecodable`() {
        val starved = VipsImageTransformer(quality = 80, Duration.ofSeconds(60), maxAddressSpace = 1024 * 1024)
        assertThrows(UndecodableImageException::class.java) {
            Files.newInputStream(fixture("sample.png")).use { starved.render(it, square(4, false)) }
        }
    }

    private companion object {
        const val DECODER_MEMORY = 2L * 1024 * 1024 * 1024
    }
}
