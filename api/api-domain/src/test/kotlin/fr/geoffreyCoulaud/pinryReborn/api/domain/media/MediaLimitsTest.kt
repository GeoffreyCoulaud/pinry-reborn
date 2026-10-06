package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MediaLimitsTest {
    private val limits =
        MediaLimits(
            maxImageBytes = 30, maxVideoBytes = 40, Duration.ofSeconds(120), maxPixelsPerFrame = 50,
            maxPixelsPerRender = 50, renderConcurrency = 1, Duration.ofSeconds(60), decoderMemory = 1,
        )

    // Three frames, so a bound that counted every frame would refuse the image one pixel under it.
    private fun image(width: Int, height: Int, bytes: Long = 30) =
        ProbeResult(MediaFormat.GIF, width, height, frames = 3, bytes)

    private fun video(width: Int, height: Int, bytes: Long = 40) =
        VideoProbeResult(
            VideoCodec.H264, null, width, height, Duration.ofSeconds(1), frames = 25, bytes, "avc1.640015",
            VideoContainer.MP4, alreadyRepackaged = true, videoBitRate = bytes * 8, sound = null,
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

    // The defaults of `media.max_pixels_per_frame` and `media.max_pixels_per_render`.
    private val defaults = limits.copy(maxPixelsPerFrame = 50_000_000, maxPixelsPerRender = 8_000_000_000)

    private fun gif(frames: Int) =
        Media.AnimatedImage(
            UUID.randomUUID(), UUID.randomUUID(), "image/gif", 7000, 7000, 1, "h", "k", Instant.EPOCH, frames,
        )

    private fun clip(width: Int, height: Int, frames: Int = 7200, duration: Duration = Duration.ofSeconds(120)) =
        Media.Video(
            UUID.randomUUID(), UUID.randomUUID(), "video/mp4", width, height, 1, "h", "k", Instant.EPOCH, frames,
            duration, videoBitRate = 1, sound = null,
        )

    @Test
    fun `Given a 7000x7000 GIF of 1000 frames, Then its animated rendition keeps its first frame`() {
        // 49 Gpx decoded, past the 8 Gpx a render may decode
        assertEquals(RenditionMode.FIRST_FRAME, defaults.renditionOf(gif(1000), 480, animated = true))
    }

    @Test
    fun `Given a 7000x7000 GIF of 10 frames, Then its animated rendition is whole`() {
        assertEquals(RenditionMode.WHOLE, defaults.renditionOf(gif(10), 480, animated = true))
    }

    @Test
    fun `Given a 7000x7000 GIF of 1000 frames asked static, Then its static rendition is whole`() {
        assertEquals(RenditionMode.WHOLE, defaults.renditionOf(gif(1000), 480, animated = false))
    }

    @Test
    fun `Given a 16 by 9 video, Then its poster at 960 is drawn from one frame`() {
        // 100 frames of 960x1706 held by thumbnail: 164 MP, past the 50 MP per frame
        assertEquals(RenditionMode.ONE_FRAME_POSTER, defaults.renditionOf(clip(1920, 1080), 960, animated = false))
    }

    @Test
    fun `Given a 16 by 9 video, Then its poster at 480 is whole`() {
        // 100 frames of 480x853: 41 MP
        assertEquals(RenditionMode.WHOLE, defaults.renditionOf(clip(1920, 1080), 480, animated = false))
    }

    @Test
    fun `Given an 8K 60 fps clip of 120 seconds, Then its animated rendition decodes three seconds and is whole`() {
        // 180 frames of 33 MP: 6 Gpx
        assertEquals(RenditionMode.WHOLE, defaults.renditionOf(clip(7680, 4320), 480, animated = true))
    }

    @Test
    fun `Given an 8K 120 fps clip, Then its animated rendition past the per-render bound is its poster`() {
        // 360 frames of 33 MP: 12 Gpx
        val fast = clip(7680, 4320, frames = 14_400)

        assertEquals(RenditionMode.FIRST_FRAME, defaults.renditionOf(fast, 480, animated = true))
        assertEquals(RenditionMode.ONE_FRAME_POSTER, defaults.renditionOf(fast, 960, animated = true))
    }

    @Test
    fun `Given a 1080p video of three seconds, Then its animated rendition counts every frame`() {
        // 7200 frames of 2 MP: 15 Gpx, the preview's three seconds being the whole video
        val short = clip(1920, 1080, duration = Duration.ofSeconds(3))

        assertEquals(RenditionMode.FIRST_FRAME, defaults.renditionOf(short, 480, animated = true))
    }

    @Test
    fun `Given a poster whose hundred decoded frames are past the per-render bound, Then it is drawn from one frame`() {
        // 100 frames of 10x10 decoded: 10,000 pixels, against 100 held at the output's 1x1
        val bounded = limits.copy(maxPixelsPerFrame = 10_000, maxPixelsPerRender = 9_999)

        assertEquals(RenditionMode.ONE_FRAME_POSTER, bounded.renditionOf(clip(10, 10, frames = 100), 1, false))
    }

    @Test
    fun `Given a frame past the per-frame bound, Then an image and a video have no rendition`() {
        // 8000x7000 is 56 MP, stored before the bound was lowered
        val image =
            Media.StillImage(UUID.randomUUID(), UUID.randomUUID(), "image/png", 8000, 7000, 1, "h", "k", Instant.EPOCH)

        assertEquals(RenditionMode.NONE, defaults.renditionOf(image, 480, animated = false))
        assertEquals(RenditionMode.NONE, defaults.renditionOf(clip(8000, 7000), 480, animated = true))
    }
}
