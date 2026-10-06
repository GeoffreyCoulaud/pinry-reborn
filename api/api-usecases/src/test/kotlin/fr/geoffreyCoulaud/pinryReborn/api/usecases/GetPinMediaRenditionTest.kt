package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaRenditionUnavailableError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GetPinMediaRenditionTest {
    private val getPinMedia = mockk<GetPinMedia>()
    private val mediaStore = mockk<MediaStore>()
    private val imageTransformer = mockk<ImageTransformer>()
    private val renditionCache = mockk<RenditionCache>()
    private val videoProcessor = mockk<VideoProcessor>()

    // Bounds every rendition below fits whole, unless a test tightens one.
    private val roomy = MediaLimits(
        1, 1, Duration.ZERO, maxPixelsPerFrame = 1_000_000, maxPixelsPerRender = 1_000_000, renderConcurrency = 2,
        decoderTimeout = Duration.ofSeconds(60), decoderMemory = 2_147_483_648,
    )

    // Moved forward by the tests that age a failure marker.
    private var now = Instant.parse("2026-10-04T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@GetPinMediaRenditionTest.now
    }

    private fun useCase(limits: MediaLimits = roomy) =
        GetPinMediaRendition(getPinMedia, mediaStore, imageTransformer, renditionCache, videoProcessor, limits, clock)

    private val useCase = useCase()

    private val requester = mockk<User>()

    private fun media(pinId: UUID, width: Int, height: Int, animated: Boolean, frames: Int = 2): Media {
        val key = "originals/u/$pinId/i.png"
        return if (animated) {
            Media.AnimatedImage(randomUUID(), pinId, "image/png", width, height, 1, "h", key, Instant.EPOCH, frames)
        } else {
            Media.StillImage(randomUUID(), pinId, "image/png", width, height, 1, "h", key, Instant.EPOCH)
        }
    }

    private fun video(pinId: UUID, frames: Int = 2) =
        Media.Video(
            randomUUID(), pinId, "video/mp4; codecs=\"avc1.64000c\"", 160, 120, 1, "h", "originals/u/$pinId/v.mp4",
            Instant.EPOCH, frames, Duration.ofSeconds(1), videoBitRate = 8, sound = null,
        )

    private val original = StagedFile("/tmp/original", 1, "h")
    private val poster = StagedFile("/tmp/poster.png", 2, "p")
    private val rendered = StagedFile("/tmp/out.webp", 3, "hh")

    // The strict store answers no openStream or stage: a video's original is staged in place, never copied.
    private fun stubVideoMiss(video: Media, key: String) {
        every { renditionCache.openStream(video.id, any()) } returns null
        every { renditionCache.markedAt(video.id, any()) } returns null
        every { mediaStore.stageStored(video) } returns original
        every { mediaStore.discard(any()) } returns Unit
        every { renditionCache.store(video.id, key, any()) } returns Unit
    }

    private fun stubMiss(img: Media, key: String) {
        every { renditionCache.openStream(img.id, any()) } returns null
        every { renditionCache.markedAt(img.id, any()) } returns null
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
    fun `Given a video whose preview times out, Then it is unavailable, marked failed, and its original discarded`() {
        // Given
        val pinId = randomUUID()
        val video = video(pinId)
        every { getPinMedia.get(pinId, requester) } returns video
        stubVideoMiss(video, "v2-40-a.webp")
        every { videoProcessor.preview(original, 40) } throws VideoProcessorTimeoutException("slow")
        every { renditionCache.mark(video.id, any()) } returns Unit

        // When / Then
        assertThrows(MediaRenditionUnavailableError::class.java) {
            useCase.get(pinId, requester, requestedPx = 40, animated = true)
        }
        verify { renditionCache.mark(video.id, "v2-40-a.webp.failed-60-2147483648") }
        verify { mediaStore.discard(original) }
    }

    @Test
    fun `Given an animated image past the per-render bound, Then it gets the static key, the animated once raised`() {
        // Given: 10 frames of 100x80 decode 80,000 pixels
        val pinId = randomUUID()
        val img = media(pinId, 100, 80, animated = true, frames = 10)
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
        val video = video(pinId, frames = 200)
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

    // Below, requests meet: a cache that remembers and a transformer that holds each render until released.

    private inner class MemoryCache : RenditionCache {
        val entries: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val marks = ConcurrentHashMap<String, Instant>()

        override fun openStream(mediaId: UUID, key: String) =
            if ("$mediaId/$key" in entries) ByteArrayInputStream(byteArrayOf()) else null

        override fun store(mediaId: UUID, key: String, staged: StagedFile) {
            entries += "$mediaId/$key"
        }

        override fun mark(mediaId: UUID, key: String) {
            marks["$mediaId/$key"] = now
        }

        override fun markedAt(mediaId: UUID, key: String) = marks["$mediaId/$key"]

        override fun evictMedia(mediaId: UUID) = error("unused")

        override fun forEachMediaIdOnDisk(block: (Sequence<UUID>) -> Unit) = error("unused")
    }

    private class HeldTransformer : ImageTransformer {
        val release = CountDownLatch(1)
        val renders = AtomicInteger()
        val failures = ConcurrentLinkedQueue<Exception>()

        override fun render(source: InputStream, spec: RenditionSpec): StagedFile {
            renders.incrementAndGet()
            release.await()
            failures.poll()?.let { throw it }
            return StagedFile("/tmp/out.webp", 3, "hh")
        }
    }

    private class Request(call: () -> ServedMedia) {
        val result = FutureTask(call)
        val thread = Thread(result).apply { start() }
    }

    private val cache = MemoryCache()
    private val held = HeldTransformer()

    private fun meeting(limits: MediaLimits = roomy.copy(renderConcurrency = 1)) =
        GetPinMediaRendition(getPinMedia, mediaStore, held, cache, videoProcessor, limits, clock)

    private fun image(): Media {
        val img = media(randomUUID(), 100, 80, animated = false)
        every { getPinMedia.get(img.pinId, requester) } returns img
        every { mediaStore.openStream(img.storageKey) } answers { ByteArrayInputStream(byteArrayOf(1)) }
        return img
    }

    private fun GetPinMediaRendition.request(img: Media) = Request { get(img.pinId, requester, 40, null) }

    // Parked on the transformer's latch or on a permit, no request can move until the latch opens.
    private fun awaitParked(requests: List<Request>) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (requests.any { it.thread.state != Thread.State.WAITING }) {
            check(System.nanoTime() < deadline) { "never parked: ${requests.map { it.thread.state }}" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `Given four cold misses under two permits, Then two render while two wait their turn`() {
        // Given
        val useCase = meeting(roomy.copy(renderConcurrency = 2))
        val images = List(4) { image() }

        // When
        val requests = images.map { useCase.request(it) }
        awaitParked(requests)

        // Then
        assertEquals(2, held.renders.get())
        held.release.countDown()
        requests.forEach { it.result.get(10, TimeUnit.SECONDS) }
        assertEquals(4, held.renders.get())
    }

    @Test
    fun `Given the only permit held by a render, Then a cached rendition is served without waiting`() {
        // Given
        val useCase = meeting()
        val rendering = useCase.request(image())
        awaitParked(listOf(rendering))
        val hit = image()
        cache.store(hit.id, "v2-40-s.webp", rendered)

        // When
        val served = assertTimeoutPreemptively(Duration.ofSeconds(5)) { useCase.get(hit.pinId, requester, 40, null) }

        // Then
        assertEquals(ServedMedia.Rendition(hit.id, "v2-40-s.webp"), served)
        held.release.countDown()
        rendering.result.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun `Given one permit and a render whose process cannot start, Then the next request renders`() {
        // Given
        val useCase = meeting()
        val img = image()
        held.release.countDown()
        held.failures += IOException("cannot start vips")

        // When
        assertThrows(IOException::class.java) { useCase.get(img.pinId, requester, 40, null) }
        val served = assertTimeoutPreemptively(Duration.ofSeconds(5)) { useCase.get(img.pinId, requester, 40, null) }

        // Then
        assertEquals(ServedMedia.Rendition(img.id, "v2-40-s.webp"), served)
        assertEquals(2, held.renders.get())
    }

    @Test
    fun `Given a waiter whose rendition lands while it waits, Then it serves it and renders nothing`() {
        // Given: the second request takes its turn behind the first, for the same key
        val useCase = meeting()
        val img = image()
        val first = useCase.request(img)
        awaitParked(listOf(first))
        val second = useCase.request(img)
        awaitParked(listOf(first, second))

        // When
        held.release.countDown()

        // Then
        assertEquals(first.result.get(10, TimeUnit.SECONDS), second.result.get(10, TimeUnit.SECONDS))
        assertEquals(1, held.renders.get())
    }

    @Test
    fun `Given a waiter whose rendition fails while it waits, Then it is unavailable and renders nothing`() {
        // Given
        val useCase = meeting()
        val img = image()
        held.failures += UndecodableImageException("vips refused it")
        val first = useCase.request(img)
        awaitParked(listOf(first))
        val second = useCase.request(img)
        awaitParked(listOf(first, second))

        // When
        held.release.countDown()

        // Then
        for (request in listOf(first, second)) {
            val thrown = assertThrows(ExecutionException::class.java) { request.result.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(MediaRenditionUnavailableError::class.java, thrown.cause)
        }
        assertEquals(1, held.renders.get())
    }

    @Test
    fun `Given a decoder that failed, Then its media is unavailable, and a new timeout or memory renders it again`() {
        // Given: the first two renders fail, the third succeeds
        val img = image()
        held.release.countDown()
        repeat(2) { held.failures += UndecodableImageException("vips ran past 60s and was destroyed") }
        fun unavailable(limits: MediaLimits) = assertThrows(MediaRenditionUnavailableError::class.java) {
            meeting(limits).get(img.pinId, requester, 40, null)
        }

        // When / Then: the failure is not replayed under the same bounds
        unavailable(roomy)
        unavailable(roomy)
        assertEquals(1, held.renders.get())
        // When / Then: a longer timeout renders again, and fails again
        unavailable(roomy.copy(decoderTimeout = Duration.ofSeconds(120)))
        assertEquals(2, held.renders.get())
        // When / Then: more memory renders again, and succeeds
        val served = meeting(roomy.copy(decoderMemory = 4_294_967_296)).get(img.pinId, requester, 40, null)
        assertEquals(ServedMedia.Rendition(img.id, "v2-40-s.webp"), served)
        assertEquals(3, held.renders.get())
    }

    @Test
    fun `Given a decoder that failed, Then its media stays unavailable for 24 hours and renders again past them`() {
        // Given: the first render fails, the second succeeds
        val useCase = meeting()
        val img = image()
        held.release.countDown()
        held.failures += UndecodableImageException("vips was killed")
        assertThrows(MediaRenditionUnavailableError::class.java) { useCase.get(img.pinId, requester, 40, null) }

        // When / Then: a marker 24 hours old still holds
        now += Duration.ofHours(24)
        assertThrows(MediaRenditionUnavailableError::class.java) { useCase.get(img.pinId, requester, 40, null) }
        assertEquals(1, held.renders.get())

        // When / Then: an older one is rendered over
        now += Duration.ofMillis(1)
        assertEquals(ServedMedia.Rendition(img.id, "v2-40-s.webp"), useCase.get(img.pinId, requester, 40, null))
        assertEquals(2, held.renders.get())
    }
}
