package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `Given H265 tagged hev1 with AAC in QuickTime, Then probe names it hvc1 from its configuration record`() {
        val result = processor.probe(staged("h265-hev1-aac.mov"), maxDuration)
        assertEquals(VideoCodec.H265, result.videoCodec)
        assertEquals("hvc1.1.6.L30.90,mp4a.40.2", result.codecs)
    }

    @Test
    fun `Given VP9 with Opus in WebM, Then probe spells Opus as WebM does`() {
        val result = processor.probe(staged("vp9-opus.webm"), maxDuration)
        assertEquals(AudioCodec.OPUS, result.audioCodec)
        assertEquals("vp09.00.10.08,opus", result.codecs)
    }

    @Test
    fun `Given AV1 without audio in MP4, Then probe returns no audio codec`() {
        val result = processor.probe(staged("av1.mp4"), maxDuration)
        assertEquals(VideoCodec.AV1, result.videoCodec)
        assertNull(result.audioCodec)
        assertEquals("av01.0.00M.08", result.codecs)
    }

    @Test
    fun `Given a video rotated a quarter turn, Then probe swaps its width and height`() {
        val result = processor.probe(staged("rotated.mp4"), maxDuration)
        assertEquals(120 to 160, result.width to result.height)
    }

    @Test
    fun `Given H264 with AC-3, Then probe refuses it naming the codec`() {
        val exception =
            assertThrows(VideoCodecUnsupportedException::class.java) {
                processor.probe(staged("h264-ac3.mkv"), maxDuration)
            }
        assertTrue(exception.message.orEmpty().contains("ac3"))
    }

    @Test
    fun `Given a clip of 121 seconds, Then probe refuses it as too long`() {
        assertThrows(VideoTooLongException::class.java) {
            processor.probe(staged("too-long.mkv"), maxDuration)
        }
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
    fun `Given an AVIF still, Then probe refuses it`() {
        assertThrows(UndecodableVideoException::class.java) {
            processor.probe(staged("still.avif"), maxDuration)
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
