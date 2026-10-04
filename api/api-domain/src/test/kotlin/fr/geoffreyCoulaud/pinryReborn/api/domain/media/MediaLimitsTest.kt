package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class MediaLimitsTest {
    private val limits =
        MediaLimits(maxImageBytes = 30, maxVideoBytes = 40, Duration.ofSeconds(120), maxPixelsPerFrame = 50)

    // Three frames, so a bound that counted every frame would refuse the image one pixel under it.
    private fun image(width: Int, height: Int, bytes: Long = 30) =
        ProbeResult(MediaFormat.GIF, width, height, frames = 3, bytes)

    private fun video(width: Int, height: Int, bytes: Long = 40) =
        VideoProbeResult(
            VideoCodec.H264, null, width, height, Duration.ofSeconds(1), frames = 25, bytes, "avc1.640015",
            VideoContainer.MP4, alreadyRepackaged = true,
        )

    @Test
    fun `Given an image one pixel under the per-frame bound, Then it is admitted`() {
        // 7 by 7 is 49 pixels
        limits.refuseIfOver(image(7, 7))
    }

    @Test
    fun `Given an image one pixel over the per-frame bound, Then it is refused for its pixels`() {
        // 51 by 1 is 51 pixels
        assertThrows(ImageTooManyPixelsException::class.java) { limits.refuseIfOver(image(51, 1)) }
    }

    @Test
    fun `Given a video one pixel under the per-frame bound, Then it is admitted`() {
        limits.refuseIfOver(video(7, 7))
    }

    @Test
    fun `Given a video one pixel over the per-frame bound, Then it is refused as an image's pixels are`() {
        val thrown = assertThrows(ImageTooManyPixelsException::class.java) { limits.refuseIfOver(video(51, 1)) }

        assertEquals("51x1, past 50 pixels per frame", thrown.message)
    }

    @Test
    fun `Given an image past its byte bound but under the video's, Then it is refused for its bytes`() {
        assertThrows(MediaTooLargeException::class.java) { limits.refuseIfOver(image(7, 7, bytes = 31)) }
    }

    @Test
    fun `Given a video past its byte bound, Then it is refused for its bytes`() {
        val thrown = assertThrows(MediaTooLargeException::class.java) { limits.refuseIfOver(video(7, 7, bytes = 41)) }

        assertEquals("41 bytes, past 40", thrown.message)
    }

    @Test
    fun `Given an image and a video bound, Then staging admits the larger of the two`() {
        assertEquals(40, limits.maxStagedBytes)
    }
}
