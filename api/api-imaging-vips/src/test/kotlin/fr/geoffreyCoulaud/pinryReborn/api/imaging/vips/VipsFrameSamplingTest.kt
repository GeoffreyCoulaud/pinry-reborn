package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.LumaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Runs the `vips` and `vipsheader` on the `PATH`, and `ffmpeg` to draw the animated images. */
class VipsFrameSamplingTest {
    private val sampler = VipsImageTransformer(quality = 80, Duration.ofSeconds(60), DECODER_MEMORY)

    @TempDir lateinit var directory: Path

    private fun media(animated: Boolean) =
        if (animated) {
            Media.AnimatedImage(UUID.randomUUID(), UUID.randomUUID(), "image/gif", 1, 1, 0, "", "", Instant.EPOCH, 2)
        } else {
            Media.StillImage(UUID.randomUUID(), UUID.randomUUID(), "image/gif", 1, 1, 0, "", "", Instant.EPOCH)
        }

    private fun framesOf(image: Path, animated: Boolean): List<LumaFrame> {
        val frames = mutableListOf<LumaFrame>()
        sampler.sample(media(animated), StagedFile(image.toString(), 0, ""), frames::add)
        return frames
    }

    // A GIF of testsrc2's frames, each shown 1 / rate seconds.
    private fun gif(frames: Int, rate: Int): Path {
        val output = directory.resolve("${frames}x$rate.gif")
        val source = listOf("-f", "lavfi", "-i", "testsrc2=size=64x48:rate=$rate")
        val command = listOf("ffmpeg", "-v", "error", "-y") + source + listOf("-frames:v", "$frames", "$output")
        assertEquals(0, ProcessBuilder(command).start().waitFor())
        return output
    }

    // Each graphic control extension (0x21 0xF9 0x04) carries its frame's delay in its fourth and fifth bytes.
    private fun withoutDelays(gif: Path): Path {
        val bytes = Files.readAllBytes(gif)
        val extension = byteArrayOf(0x21, 0xF9.toByte(), 0x04)
        for (index in 0 until bytes.size - 5) {
            if (bytes.copyOfRange(index, index + 3).contentEquals(extension)) {
                bytes[index + 4] = 0
                bytes[index + 5] = 0
            }
        }
        return Files.write(gif, bytes)
    }

    @Test
    fun `Given a GIF of three frames shown half a second each, Then each frame is sampled once`() {
        assertEquals(3, framesOf(Path.of("src/test/resources/fixtures/animated.gif"), animated = true).size)
    }

    @Test
    fun `Given a GIF of eight frames shown a second each, Then every frame is sampled`() {
        assertEquals(8, framesOf(gif(frames = 8, rate = 1), animated = true).size)
    }

    @Test
    fun `Given a GIF of forty frames shown a tenth of a second each, Then one frame a second is sampled`() {
        assertEquals(4, framesOf(gif(frames = 40, rate = 10), animated = true).size)
    }

    @Test
    fun `Given a GIF of ten frames whose delays are all zero, Then four of them are sampled`() {
        assertEquals(4, framesOf(withoutDelays(gif(frames = 10, rate = 1)), animated = true).size)
    }

    @Test
    fun `Given a large transparent PNG, Then its one frame is bounded, flattened on white, and leaves no file`() {
        // Given: four bands of zeros, which vips saves as black wholly transparent
        val png = directory.resolve("transparent.png")
        val command = listOf("vips", "black", "$png", "1024", "768", "--bands", "4")
        assertEquals(0, ProcessBuilder(command).start().waitFor())
        // When
        val frames = framesOf(png, animated = false)
        // Then
        assertEquals(listOf(512 to 384), frames.map { it.width to it.height })
        assertTrue(frames.single().luma.all { it > 254.5f })
        assertEquals(listOf(png), Files.list(directory).use { it.toList() })
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "[bitdepth=16]"])
    fun `Given a white grey PNG of either depth, Then its one frame reads white`(depth: String) {
        // Given: one band, inverted from black
        val black = directory.resolve("black.png")
        val white = directory.resolve("white.png")
        assertEquals(0, ProcessBuilder("vips", "black", "$black", "64", "48").start().waitFor())
        assertEquals(0, ProcessBuilder("vips", "invert", "$black", "$white$depth").start().waitFor())
        // When
        val frames = framesOf(white, animated = false)
        // Then
        assertTrue(frames.single().luma.all { it > 254.5f })
    }

    @Test
    fun `Given a delay vipsheader cannot have written, Then the sampling is reported undecodable`() {
        assertThrows(UndecodableImageException::class.java) { sampler.delaysOf("100 -") }
    }

    @Test
    fun `Given a file vips refuses, Then the sampling is reported undecodable`() {
        assertThrows(UndecodableImageException::class.java) {
            framesOf(Path.of("src/test/resources/fixtures/not-an-image.txt"), animated = false)
        }
    }

    private companion object {
        const val DECODER_MEMORY = 2L * 1024 * 1024 * 1024
    }
}
