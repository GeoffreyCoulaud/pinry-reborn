package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

class FfmpegVideoProcessorTest {
    private val processor = FfmpegVideoProcessor(Duration.ofSeconds(60))
    private val maxDuration = Duration.ofSeconds(120)

    private fun staged(name: String) =
        StagedFile(path = Path.of("src/test/resources/fixtures", name).toString(), byteSize = 0, contentHash = "")

    @Test
    fun `Given H264 with AAC in Matroska, Then probe returns its codecs, dimensions and duration`() {
        // When
        val result = processor.probe(staged("h264-aac.mkv"), maxDuration)
        // Then
        assertEquals(VideoCodec.H264, result.videoCodec)
        assertEquals(AudioCodec.AAC, result.audioCodec)
        assertEquals(160 to 120, result.width to result.height)
        assertEquals(Duration.ofMillis(1_023), result.duration)
        assertEquals("avc1.64000A,mp4a.40.2", result.codecs)
    }

    @Test
    fun `Given an MPEG-TS, Then probe refuses it at opening`() {
        assertThrows(UndecodableVideoException::class.java) {
            processor.probe(staged("mpegts.ts"), maxDuration)
        }
    }

    @Test
    fun `Given an HLS playlist pointing at a local file, Then probe refuses it at opening`() {
        assertThrows(UndecodableVideoException::class.java) {
            processor.probe(staged("playlist.m3u8"), maxDuration)
        }
    }

    @Test
    fun `Given a timeout ffprobe cannot meet, Then the process is destroyed and reported`() {
        // Given
        val impatient = FfmpegVideoProcessor(Duration.ZERO)
        // When
        assertThrows(VideoProcessorTimeoutException::class.java) {
            impatient.probe(staged("h264-aac.mkv"), maxDuration)
        }
        // Then
        assertEquals(0, ProcessHandle.current().children().count())
    }
}
