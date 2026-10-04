package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaRenditionUnavailableError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Duration
import java.util.UUID
import java.util.UUID.randomUUID

class GetPinMediaRenditionTest {
    private val getPinMedia = mockk<GetPinMedia>()
    private val mediaStore = mockk<MediaStore>()
    private val imageTransformer = mockk<ImageTransformer>()
    private val renditionCache = mockk<RenditionCache>()
    private val videoProcessor = mockk<VideoProcessor>()

    // Bounds every rendition below fits whole, unless a test tightens one.
    private val roomy = MediaLimits(1, 1, Duration.ZERO, maxPixelsPerFrame = 1_000_000, maxPixelsPerRender = 1_000_000)

    private fun useCase(limits: MediaLimits = roomy) =
        GetPinMediaRendition(getPinMedia, mediaStore, imageTransformer, renditionCache, videoProcessor, limits)

    private val useCase = useCase()

    private val requester = mockk<User>()

    private fun media(pinId: UUID, width: Int, height: Int, animated: Boolean) = Media(
        id = randomUUID(), pinId = pinId, mimeType = "image/png", width = width, height = height,
        animated = animated, byteSize = 1, contentHash = "h",
        storageKey = "originals/u/$pinId/i.png", createdAt = java.time.Instant.EPOCH,
    )

    private fun video(pinId: UUID) =
        media(pinId, 160, 120, animated = true).copy(mimeType = "video/mp4; codecs=\"avc1.64000c\"")

    private val original = StagedFile("/tmp/original", 1, "h")
    private val poster = StagedFile("/tmp/poster.png", 2, "p")
    private val rendered = StagedFile("/tmp/out.webp", 3, "hh")

    // The strict store answers no openStream or stage: a video's original is staged in place, never copied.
    private fun stubVideoMiss(video: Media, key: String) {
        every { renditionCache.openStream(video.id, key) } returns null
        every { mediaStore.stageStored(video) } returns original
        every { mediaStore.discard(any()) } returns Unit
        every { renditionCache.store(video.id, key, any()) } returns Unit
    }

    private fun stubMiss(img: Media, key: String) {
        every { renditionCache.openStream(img.id, key) } returns null
        every { mediaStore.openStream(img.storageKey) } returns ByteArrayInputStream(byteArrayOf(1))
        every { imageTransformer.render(any(), any()) } returns StagedFile("/tmp/out.webp", 3, "hh")
        every { renditionCache.store(img.id, key, any()) } returns Unit
    }

    @Test
    fun `Given no size, Then it serves the original`() {
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img

        val served = useCase.get(pinId, requester, requestedPx = null, animated = true)

        assertEquals(ServedMedia.Original(img), served)
    }

    @Test
    fun `Given a static image at least as small as the size, Then it serves the original`() {
        val pinId = randomUUID()
        val img = media(pinId, 10, 20, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        assertEquals(ServedMedia.Original(img), served)
    }

    @Test
    fun `Given a static image larger than the size and a cache miss, Then it renders, stores, and serves it`() {
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img
        // A static source renders statically whatever was requested: the flag is intersected away.
        stubMiss(img, "v2-40-s.webp")

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v2-40-s.webp", rendition.key)
        verify { imageTransformer.render(any(), RenditionSpec(40, false, 100, 80)) }
        verify { renditionCache.store(img.id, "v2-40-s.webp", any()) }
    }

    @Test
    fun `Given a static image, Then requesting it static hits the same key as requesting it animated`() {
        // Given: the same static source, this time with animated = false explicitly
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v2-40-s.webp")

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = false)

        // Then: identical bytes dedup onto one cache entry and one ETag, whatever the client asked
        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v2-40-s.webp", rendition.key)
        verify { imageTransformer.render(any(), RenditionSpec(40, false, 100, 80)) }
    }

    @Test
    fun `Given an animated image larger than the size, Then it renders an animated rendition`() {
        // Given
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = true)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v2-40-a.webp")

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        // Then: an animated source is the only case that reaches the transformer with animated=true
        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v2-40-a.webp", rendition.key)
        verify { imageTransformer.render(any(), RenditionSpec(40, true, 100, 80)) }
    }

    @Test
    fun `Given a cache hit, Then it serves the rendition without rendering`() {
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img
        every { renditionCache.openStream(img.id, "v2-40-s.webp") } returns ByteArrayInputStream(byteArrayOf(9))

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        assertInstanceOf(ServedMedia.Rendition::class.java, served)
        verify(exactly = 0) { imageTransformer.render(any(), any()) }
        verify(exactly = 0) { renditionCache.store(any(), any(), any()) }
    }

    @Test
    fun `Given an animated image and animated = false at a large size, Then it flattens to a rendition`() {
        val pinId = randomUUID()
        val img = media(pinId, 10, 20, animated = true)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v2-10-s.webp")

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = false)

        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v2-10-s.webp", rendition.key)
        verify { imageTransformer.render(any(), RenditionSpec(10, false, 10, 20)) }
    }

    @Test
    fun `Given an animated image and animated = true at a large size, Then it serves the original`() {
        val pinId = randomUUID()
        val img = media(pinId, 10, 20, animated = true)
        every { getPinMedia.get(pinId, requester) } returns img

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        assertEquals(ServedMedia.Original(img), served)
    }

    @Test
    fun `Given an animated image and no animated flag, Then the rendition keeps its animation`() {
        // Given
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = true)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v2-40-a.webp")

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = null)

        // Then
        assertEquals("v2-40-a.webp", assertInstanceOf(ServedMedia.Rendition::class.java, served).key)
    }

    @Test
    fun `Given a video and no size, Then it serves the original`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video

        // When
        val served = useCase.get(pinId, requester, requestedPx = null, animated = null)

        // Then
        assertEquals(ServedMedia.Original(video), served)
    }

    @Test
    fun `Given a video and no animated flag, Then its poster is drawn at the size and nothing stays staged`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-40-s.webp")
        every { videoProcessor.poster(original, 40, fromOneFrame = false) } returns poster
        every { mediaStore.openStaged(poster) } returns ByteArrayInputStream(byteArrayOf(2))
        every { imageTransformer.render(any(), RenditionSpec(40, false, 160, 120)) } returns rendered

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = null)

        // Then
        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v2-40-s.webp", rendition.key)
        verify { renditionCache.store(video.id, "v2-40-s.webp", rendered) }
        verify { mediaStore.discard(poster) }
        verify { mediaStore.discard(original) }
    }

    @Test
    fun `Given a video and animated = true, Then its preview is the rendition`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-40-a.webp")
        every { videoProcessor.preview(original, 40) } returns rendered

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        // Then
        assertEquals("v2-40-a.webp", assertInstanceOf(ServedMedia.Rendition::class.java, served).key)
        verify { renditionCache.store(video.id, "v2-40-a.webp", rendered) }
        verify { mediaStore.discard(original) }
        verify(exactly = 0) { imageTransformer.render(any(), any()) }
    }

    @Test
    fun `Given a video smaller than the size, Then it still takes a rendition, at its shortest side`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-120-a.webp")
        every { videoProcessor.preview(original, 120) } returns rendered

        // When
        val served = useCase.get(pinId, requester, requestedPx = 960, animated = true)

        // Then
        assertEquals("v2-120-a.webp", assertInstanceOf(ServedMedia.Rendition::class.java, served).key)
    }

    @Test
    fun `Given a video whose preview fails, Then the failure propagates and the staged original is discarded`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-40-a.webp")
        every { videoProcessor.preview(original, 40) } throws VideoProcessorTimeoutException("slow")

        // When / Then
        assertThrows(VideoProcessorTimeoutException::class.java) {
            useCase.get(pinId, requester, requestedPx = 40, animated = true)
        }
        verify { mediaStore.discard(original) }
    }

    @Test
    fun `Given an animated image past the per-render bound, Then it gets the static key, the animated once raised`() {
        // Given: 10 frames of 100x80 decode 80,000 pixels
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = true).copy(frames = 10)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v2-40-s.webp")
        stubMiss(img, "v2-40-a.webp")
        val tight = useCase(roomy.copy(maxPixelsPerRender = 79_999))

        // When
        val degraded = tight.get(pinId, requester, requestedPx = 40, animated = true)
        val whole = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        // Then
        assertEquals("v2-40-s.webp", assertInstanceOf(ServedMedia.Rendition::class.java, degraded).key)
        assertEquals("v2-40-a.webp", assertInstanceOf(ServedMedia.Rendition::class.java, whole).key)
        verify { imageTransformer.render(any(), RenditionSpec(40, false, 100, 80)) }
        verify { imageTransformer.render(any(), RenditionSpec(40, true, 100, 80)) }
    }

    @Test
    fun `Given a video whose preview decodes past the per-render bound, Then its poster takes the static key`() {
        // Given: 200 frames of 160x120 decode 3,840,000 pixels, the poster's 100 half that
        val pinId = randomUUID()
        val video = video(pinId).copy(frames = 200)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-40-s.webp")
        every { videoProcessor.poster(original, 40, fromOneFrame = false) } returns poster
        every { mediaStore.openStaged(poster) } returns ByteArrayInputStream(byteArrayOf(2))
        every { imageTransformer.render(any(), RenditionSpec(40, false, 160, 120)) } returns rendered

        // When
        val served = useCase(roomy.copy(maxPixelsPerRender = 2_000_000)).get(pinId, requester, 40, animated = true)

        // Then
        assertEquals("v2-40-s.webp", assertInstanceOf(ServedMedia.Rendition::class.java, served).key)
        verify(exactly = 0) { videoProcessor.preview(any(), any()) }
    }

    @Test
    fun `Given a video whose poster holds too many pixels, Then it is drawn from one frame under its own key`() {
        // Given: thumbnail would hold 100 frames of 40x53, 213,300 pixels
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-40-s1.webp")
        every { videoProcessor.poster(original, 40, fromOneFrame = true) } returns poster
        every { mediaStore.openStaged(poster) } returns ByteArrayInputStream(byteArrayOf(2))
        every { imageTransformer.render(any(), RenditionSpec(40, false, 160, 120)) } returns rendered

        // When
        val served = useCase(roomy.copy(maxPixelsPerFrame = 200_000)).get(pinId, requester, 40, animated = null)

        // Then
        assertEquals("v2-40-s1.webp", assertInstanceOf(ServedMedia.Rendition::class.java, served).key)
        verify { renditionCache.store(video.id, "v2-40-s1.webp", rendered) }
    }

    @Test
    fun `Given a stored media past the per-frame bound, Then its rendition is unavailable and nothing renders`() {
        // Given: 100x80 is 8,000 pixels, stored before the bound was lowered
        val pinId = randomUUID()
        every { getPinMedia.get(pinId, requester) } returns media(pinId, 100, 80, animated = false)

        // When / Then
        assertThrows(MediaRenditionUnavailableError::class.java) {
            useCase(roomy.copy(maxPixelsPerFrame = 7_999)).get(pinId, requester, requestedPx = 40, animated = null)
        }
        verify(exactly = 0) { imageTransformer.render(any(), any()) }
    }

    @Test
    fun `Given the pin has no image, Then the guard from GetPinMedia propagates`() {
        val pinId = randomUUID()
        every { getPinMedia.get(pinId, requester) } throws MediaDoesNotExistError()

        assertThrows(MediaDoesNotExistError::class.java) {
            useCase.get(pinId, requester, requestedPx = 40, animated = true)
        }
    }
}
