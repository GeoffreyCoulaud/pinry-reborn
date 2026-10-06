package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.LumaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Runs the `ffmpeg` on the `PATH`, which also draws the videos. */
class FfmpegFrameSamplingTest {
    private val sampler = FfmpegVideoProcessor(Duration.ofSeconds(60), DECODER_MEMORY, webpQuality = 75)

    @TempDir
    lateinit var directory: Path

    private fun media(duration: Duration) =
        Media.Video(
            UUID.randomUUID(), UUID.randomUUID(), "video/mp4", 1, 1, 0, "", "", Instant.EPOCH, 2, duration, 1, null,
        )

    private fun framesOf(video: Path, duration: Duration): List<LumaFrame> {
        val frames = mutableListOf<LumaFrame>()
        sampler.sample(media(duration), StagedFile(video.toString(), 0, ""), frames::add)
        return frames
    }

    // testsrc2 at 25 frames a second, in Matroska, which the sampler's demuxers accept.
    private fun video(seconds: Int): Path {
        val output = directory.resolve("$seconds.mkv")
        val source = listOf("-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=$seconds")
        val command = listOf("ffmpeg", "-v", "error") + source + listOf("-c:v", "libx264", "$output")
        assertEquals(0, ProcessBuilder(command).start().waitFor())
        return output
    }

    private fun filesBeside(video: Path) = Files.list(video.parent).use { it.toList() }

    @Test
    fun `Given a video of ten seconds, Then one frame a second is sampled, bounded, and no file is left`() {
        // Given
        val video = video(seconds = 10)
        // When
        val frames = framesOf(video, Duration.ofSeconds(10))
        // Then
        assertEquals(List(10) { 512 to 288 }, frames.map { it.width to it.height })
        assertEquals(listOf(video), filesBeside(video))
    }

    @Test
    fun `Given a video of two seconds, Then four frames are sampled`() {
        assertEquals(4, framesOf(video(seconds = 2), Duration.ofSeconds(2)).size)
    }

    @Test
    fun `Given a file ffmpeg refuses, Then the sampling is reported undecodable and no file is left`() {
        // Given
        val text = Files.writeString(directory.resolve("not-a-video.mkv"), "not a video")
        // When
        assertThrows(UndecodableVideoException::class.java) { framesOf(text, Duration.ofSeconds(1)) }
        // Then
        assertEquals(listOf(text), filesBeside(text))
    }

    private companion object {
        const val DECODER_MEMORY = 2L * 1024 * 1024 * 1024
    }
}
