package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.UUID.randomUUID

class GetPinMediaRenditionTest {
    private val getPinMedia = mockk<GetPinMedia>()
    private val mediaStore = mockk<MediaStore>()
    private val imageTransformer = mockk<ImageTransformer>()
    private val renditionCache = mockk<RenditionCache>()
    private val videoProcessor = mockk<VideoProcessor>()
    private val useCase =
        GetPinMediaRendition(getPinMedia, mediaStore, imageTransformer, renditionCache, videoProcessor)

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
        stubMiss(img, "v1-40-s.webp")

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v1-40-s.webp", rendition.key)
        assertEquals(40, rendition.effectivePx)
        assertFalse(rendition.animated)
        verify { imageTransformer.render(any(), RenditionSpec(40, false)) }
        verify { renditionCache.store(img.id, "v1-40-s.webp", any()) }
    }

    @Test
    fun `Given a static image, Then requesting it static hits the same key as requesting it animated`() {
        // Given: the same static source, this time with animated = false explicitly
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v1-40-s.webp")

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = false)

        // Then: identical bytes dedup onto one cache entry and one ETag, whatever the client asked
        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v1-40-s.webp", rendition.key)
        assertFalse(rendition.animated)
        verify { imageTransformer.render(any(), RenditionSpec(40, false)) }
    }

    @Test
    fun `Given an animated image larger than the size, Then it renders an animated rendition`() {
        // Given
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = true)
        every { getPinMedia.get(pinId, requester) } returns img
        stubMiss(img, "v1-40-a.webp")

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        // Then: an animated source is the only case that reaches the transformer with animated=true
        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v1-40-a.webp", rendition.key)
        assertTrue(rendition.animated)
        verify { imageTransformer.render(any(), RenditionSpec(40, true)) }
    }

    @Test
    fun `Given a cache hit, Then it serves the rendition without rendering`() {
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = false)
        every { getPinMedia.get(pinId, requester) } returns img
        every { renditionCache.openStream(img.id, "v1-40-s.webp") } returns ByteArrayInputStream(byteArrayOf(9))

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
        stubMiss(img, "v1-10-s.webp")

        val served = useCase.get(pinId, requester, requestedPx = 40, animated = false)

        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v1-10-s.webp", rendition.key)
        assertEquals(10, rendition.effectivePx)
        verify { imageTransformer.render(any(), RenditionSpec(10, false)) }
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
        stubMiss(img, "v1-40-a.webp")

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = null)

        // Then
        assertTrue(assertInstanceOf(ServedMedia.Rendition::class.java, served).animated)
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
        stubVideoMiss(video, "v1-40-s.webp")
        every { videoProcessor.poster(original, 40) } returns poster
        every { mediaStore.openStaged(poster) } returns ByteArrayInputStream(byteArrayOf(2))
        every { imageTransformer.render(any(), RenditionSpec(40, false)) } returns rendered

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = null)

        // Then
        val rendition = assertInstanceOf(ServedMedia.Rendition::class.java, served)
        assertEquals("v1-40-s.webp", rendition.key)
        assertFalse(rendition.animated)
        verify { renditionCache.store(video.id, "v1-40-s.webp", rendered) }
        verify { mediaStore.discard(poster) }
        verify { mediaStore.discard(original) }
    }

    @Test
    fun `Given a video and animated = true, Then its preview is the rendition`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v1-40-a.webp")
        every { videoProcessor.preview(original, 40) } returns rendered

        // When
        val served = useCase.get(pinId, requester, requestedPx = 40, animated = true)

        // Then
        assertTrue(assertInstanceOf(ServedMedia.Rendition::class.java, served).animated)
        verify { renditionCache.store(video.id, "v1-40-a.webp", rendered) }
        verify { mediaStore.discard(original) }
        verify(exactly = 0) { imageTransformer.render(any(), any()) }
    }

    @Test
    fun `Given a video smaller than the size, Then it still takes a rendition, at its shortest side`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v1-120-a.webp")
        every { videoProcessor.preview(original, 120) } returns rendered

        // When
        val served = useCase.get(pinId, requester, requestedPx = 960, animated = true)

        // Then
        assertEquals(120, assertInstanceOf(ServedMedia.Rendition::class.java, served).effectivePx)
    }

    @Test
    fun `Given a video whose preview fails, Then the failure propagates and the staged original is discarded`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v1-40-a.webp")
        every { videoProcessor.preview(original, 40) } throws VideoProcessorTimeoutException("slow")

        // When / Then
        assertThrows(VideoProcessorTimeoutException::class.java) {
            useCase.get(pinId, requester, requestedPx = 40, animated = true)
        }
        verify { mediaStore.discard(original) }
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
