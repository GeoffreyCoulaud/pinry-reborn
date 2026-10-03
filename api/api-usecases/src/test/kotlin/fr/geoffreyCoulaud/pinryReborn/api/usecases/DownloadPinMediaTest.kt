package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchAccessDeniedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchFailedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchNotFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTooManyPixelsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.TooManyRedirectsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.exceptions.PermanentTaskException
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID

class DownloadPinMediaTest {
    private val pins: PinRepositoryInterface = mockk()
    private val mediaRepository: MediaRepositoryInterface = mockk(relaxed = true)
    private val downloads: MediaDownloadRepositoryInterface = mockk(relaxed = true)
    private val store: MediaStore = mockk(relaxed = true)
    private val probe: ImageProbe = mockk()
    private val fetcher: MediaFetcher = mockk()
    private val runner: TransactionRunner = mockk()
    private val clock: Clock = mockk()
    private val renditionCache: RenditionCache = mockk()
    private val now = Instant.parse("2026-07-10T00:00:00Z")
    private val pinId = randomUUID()
    private val user = User(randomUUID(), "u", createdAt = TestTime.now)

    private val subject =
        DownloadPinMedia(
            pins, mediaRepository, downloads, store,
            MediaIngestion(store, probe, NoVideoProcessor,MediaBounds(100, 0, Duration.ZERO, 100)), fetcher, runner,
            clock, renditionCache,
        )

    init {
        every { clock.now() } returns now
        every { renditionCache.evictMedia(any()) } returns Unit
    }

    private fun pendingRow() = MediaDownload(
        pinId, "https://x/i.png", DownloadStatus.PENDING, null, null, randomUUID(), now, now,
    )
    private fun failedRow() = MediaDownload(
        pinId, "https://x/i.png", DownloadStatus.FAILED, DownloadReason.NOT_FOUND, null, randomUUID(), now, now,
    )
    private fun pin() = Pin(pinId, user, "https://ctx", "https://x/i.png", "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)
    private fun ctx(attempt: Int = 1, max: Int = 3) = TaskContext(attempt, max)
    private fun staged() = StagedFile("tmp/x", 3, "hash")

    private fun stubUntilFetch() {
        every { downloads.findByPinId(pinId) } returns pendingRow()
        every { pins.findPinById(pinId) } returns pin()
    }

    private fun stubUntilStage() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } returns ByteArrayInputStream(byteArrayOf(1))
        every { store.stage(any(), any()) } returns staged()
    }

    @Test
    fun `Given no PENDING download row, Then it is a no-op`() {
        every { downloads.findByPinId(pinId) } returns null
        subject.download(pinId, ctx())
        verify(exactly = 0) { fetcher.openStream(any()) }
    }

    @Test
    fun `Given a FAILED download row, Then it is a no-op`() {
        every { downloads.findByPinId(pinId) } returns failedRow()
        subject.download(pinId, ctx())
        verify(exactly = 0) { fetcher.openStream(any()) }
    }

    @Test
    fun `Given the pin is gone, Then it is a no-op`() {
        every { downloads.findByPinId(pinId) } returns pendingRow()
        every { pins.findPinById(pinId) } returns null
        subject.download(pinId, ctx())
        verify(exactly = 0) { fetcher.openStream(any()) }
    }

    @Test
    fun `Given a disallowed URL, Then it marks FAILED URL_NOT_ALLOWED and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws UrlNotAllowedException("blocked")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.URL_NOT_ALLOWED, now) }
    }

    @Test
    fun `Given a 403 bounce, Then it marks FAILED ACCESS_DENIED and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws FetchAccessDeniedException("403")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.ACCESS_DENIED, now) }
    }

    @Test
    fun `Given a 404 origin, Then it marks FAILED NOT_FOUND and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws FetchNotFoundException("404")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.NOT_FOUND, now) }
    }

    @Test
    fun `Given the fetch body is too large, Then it marks FAILED TOO_LARGE and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws FetchTooLargeException("body too big")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.TOO_LARGE, now) }
    }

    @Test
    fun `Given too many redirects, Then it marks FAILED FETCH_FAILED and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws TooManyRedirectsException("loop")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.FETCH_FAILED, now) }
    }

    @Test
    fun `Given a generic fetch failure, Then it marks FAILED FETCH_FAILED and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws FetchFailedException("unexpected 418")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.FETCH_FAILED, now) }
    }

    @Test
    fun `Given an unreachable origin below the attempt limit, Then it records the error and rethrows`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws FetchUnreachableException("timeout")
        assertThrows(FetchUnreachableException::class.java) {
            subject.download(pinId, ctx(attempt = 1, max = 3))
        }
        verify { downloads.recordLastError(pinId, "timeout", now) }
    }

    @Test
    fun `Given an unreachable origin at the attempt limit, Then it marks FAILED and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } throws FetchUnreachableException("timeout")
        assertThrows(PermanentTaskException::class.java) {
            subject.download(pinId, ctx(attempt = 3, max = 3))
        }
        verify { downloads.markFailed(pinId, DownloadReason.UNREACHABLE, now) }
    }

    @Test
    fun `Given the store rejects an oversize stream, Then it marks FAILED TOO_LARGE and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } returns ByteArrayInputStream(byteArrayOf(1))
        every { store.stage(any(), any()) } throws MediaTooLargeException("too big")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.TOO_LARGE, now) }
    }

    @Test
    fun `Given an image past its byte bound once probed, Then it marks FAILED TOO_LARGE and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } returns ByteArrayInputStream(byteArrayOf(1))
        every { store.stage(any(), any()) } returns StagedFile("tmp/x", 101, "hash")
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { downloads.markFailed(pinId, DownloadReason.TOO_LARGE, now) }
    }

    @Test
    fun `Given a mid-stream stage failure below the attempt limit, Then it records the error and rethrows`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } returns ByteArrayInputStream(byteArrayOf(1))
        every { store.stage(any(), any()) } throws IOException("connection reset")
        assertThrows(IOException::class.java) {
            subject.download(pinId, ctx(attempt = 1, max = 3))
        }
        verify { downloads.recordLastError(pinId, "connection reset", now) }
    }

    @Test
    fun `Given a mid-stream stage failure at the attempt limit, Then it marks FAILED and throws Permanent`() {
        stubUntilFetch()
        every { fetcher.openStream(any()) } returns ByteArrayInputStream(byteArrayOf(1))
        every { store.stage(any(), any()) } throws IOException("connection reset")
        assertThrows(PermanentTaskException::class.java) {
            subject.download(pinId, ctx(attempt = 3, max = 3))
        }
        verify { downloads.markFailed(pinId, DownloadReason.UNREACHABLE, now) }
    }

    @Test
    fun `Given a body being fetched, Then each read offers the lease a renewal`() {
        // Given
        stubUntilStage()
        every { fetcher.openStream(any()) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { store.stage(any(), any()) } answers {
            firstArg<InputStream>().run { read(); readAllBytes() }
            staged()
        }
        every { probe.probe(any(), any()) } throws UndecodableImageException("garbage")
        var renewals = 0
        val context = ctx().apply { renewLeaseIfDue = { renewals++ } }

        // When
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, context) }

        // Then: one single-byte read, one buffered read of the rest, one read of the end
        val reads = 3
        assertEquals(reads, renewals)
    }

    @Test
    fun `Given an undecodable image, Then it discards and marks FAILED INVALID_MEDIA and throws Permanent`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } throws UndecodableImageException("garbage")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { store.discard(staged()) }
        verify { downloads.markFailed(pinId, DownloadReason.INVALID_MEDIA, now) }
    }

    @Test
    fun `Given an unsupported image format, Then it discards and marks FAILED UNSUPPORTED_CODEC`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } throws UnsupportedImageFormatException("tiff")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { store.discard(staged()) }
        verify { downloads.markFailed(pinId, DownloadReason.UNSUPPORTED_CODEC, now) }
    }

    @Test
    fun `Given a video the processor refuses, Then each refusal marks FAILED with its reason and throws Permanent`() {
        // Given
        val video = mockk<VideoProcessor>()
        val withVideo = DownloadPinMedia(
            pins, mediaRepository, downloads, store,
            MediaIngestion(store, probe, video, MediaBounds(100, 100, Duration.ofSeconds(1), 100)), fetcher, runner,
            clock, renditionCache,
        )
        stubUntilStage()
        every { probe.probe(any(), any()) } throws UndecodableImageException("not an image")
        val reasons = mapOf(
            VideoTooLongException("121 s") to DownloadReason.TOO_LONG,
            VideoCodecUnsupportedException("ac3") to DownloadReason.UNSUPPORTED_CODEC,
            UndecodableVideoException("refused by ffmpeg") to DownloadReason.INVALID_MEDIA,
        )
        for ((refusal, reason) in reasons) {
            every { video.probe(any(), any()) } returns
                VideoProbeResult(VideoCodec.H264, null, 2, 2, Duration.ofSeconds(1), "avc1.640015", VideoContainer.MP4)
            every { video.repackage(any(), any()) } throws refusal
            // When / Then
            assertThrows(PermanentTaskException::class.java) { withVideo.download(pinId, ctx()) }
            verify { downloads.markFailed(pinId, reason, now) }
        }
    }

    @Test
    fun `Given a processor timeout below the attempt limit, Then it records a retryable error`() {
        // Given
        val video = mockk<VideoProcessor>()
        val withVideo = DownloadPinMedia(
            pins, mediaRepository, downloads, store,
            MediaIngestion(store, probe, video, MediaBounds(100, 100, Duration.ofSeconds(1), 100)), fetcher, runner,
            clock, renditionCache,
        )
        stubUntilStage()
        every { probe.probe(any(), any()) } throws UndecodableImageException("not an image")
        every { video.probe(any(), any()) } throws VideoProcessorTimeoutException("ffprobe ran past PT1S")

        // When / Then
        assertThrows(VideoProcessorTimeoutException::class.java) {
            withVideo.download(pinId, ctx(attempt = 1, max = 3))
        }
        verify { downloads.recordLastError(pinId, "ffprobe ran past PT1S", now) }
    }

    @Test
    fun `Given too many pixels, Then it discards and marks FAILED TOO_MANY_PIXELS and throws Permanent`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } throws ImageTooManyPixelsException("decompression bomb")
        assertThrows(PermanentTaskException::class.java) { subject.download(pinId, ctx()) }
        verify { store.discard(staged()) }
        verify { downloads.markFailed(pinId, DownloadReason.TOO_MANY_PIXELS, now) }
    }

    @Test
    fun `Given a generic probe failure below the attempt limit, Then it discards and records a retryable error`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } throws RuntimeException("boom")
        assertThrows(RuntimeException::class.java) { subject.download(pinId, ctx(attempt = 1, max = 3)) }
        verify { store.discard(staged()) }
        verify { downloads.recordLastError(pinId, "boom", now) }
    }

    @Test
    fun `Given a successful fetch and a still-PENDING row, Then it promotes and swaps`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        every { mediaRepository.findByPinId(pinId) } returns null
        every { downloads.deleteIfPending(pinId) } returns 1
        every { runner.inTransaction<Boolean>(any()) } answers { firstArg<() -> Boolean>().invoke() }
        subject.download(pinId, ctx())
        verify { store.promote(staged(), any()) }
        verify { mediaRepository.save(any()) }
        // First-time download: no superseded image, so nothing is deleted.
        verify(exactly = 0) { store.delete(any()) }
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test
    fun `Given a still-PENDING row over an existing image, Then it swaps and deletes the superseded file`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        val supersededKey = "originals/x/$pinId/old.png"
        every { mediaRepository.findByPinId(pinId) } returns
            Media(randomUUID(), pinId, "image/png", 1, 1, false, 3, "oldhash", supersededKey, now)
        every { downloads.deleteIfPending(pinId) } returns 1
        every { runner.inTransaction<Boolean>(any()) } answers { firstArg<() -> Boolean>().invoke() }
        subject.download(pinId, ctx())
        verify { mediaRepository.save(any()) }
        // Only the superseded file is deleted; the freshly promoted new file is kept.
        verify(exactly = 1) { store.delete(supersededKey) }
        verify(exactly = 1) { store.delete(any()) }
    }

    @Test
    fun `Given a still-PENDING row over an existing image, Then it evicts the superseded image's rendition cache`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        val supersededKey = "originals/x/$pinId/old.png"
        val superseded = Media(randomUUID(), pinId, "image/png", 1, 1, false, 3, "oldhash", supersededKey, now)
        every { mediaRepository.findByPinId(pinId) } returns superseded
        every { downloads.deleteIfPending(pinId) } returns 1
        every { runner.inTransaction<Boolean>(any()) } answers { firstArg<() -> Boolean>().invoke() }
        subject.download(pinId, ctx())
        verify { renditionCache.evictMedia(superseded.id) }
    }

    @Test
    fun `Given the rendition cache eviction fails during a real swap, Then the download still succeeds`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        val supersededKey = "originals/x/$pinId/old.png"
        val superseded = Media(randomUUID(), pinId, "image/png", 1, 1, false, 3, "oldhash", supersededKey, now)
        every { mediaRepository.findByPinId(pinId) } returns superseded
        every { downloads.deleteIfPending(pinId) } returns 1
        every { runner.inTransaction<Boolean>(any()) } answers { firstArg<() -> Boolean>().invoke() }
        every { renditionCache.evictMedia(any()) } throws RuntimeException("io")
        subject.download(pinId, ctx())
        verify { mediaRepository.save(any()) }
    }

    @Test
    fun `Given the row was superseded before the swap, Then it deletes the promoted file and does not save`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        every { downloads.deleteIfPending(pinId) } returns 0
        every { runner.inTransaction<Boolean>(any()) } answers { firstArg<() -> Boolean>().invoke() }
        subject.download(pinId, ctx())
        verify { store.delete(any()) }
        verify(exactly = 0) { mediaRepository.save(any()) }
        // A no-op swap keeps the old image; its rendition cache must not be touched.
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test
    fun `Given promote fails below the attempt limit, Then it cleans up and records a retryable INTERNAL_ERROR`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        every { store.promote(any(), any()) } throws RuntimeException()
        assertThrows(RuntimeException::class.java) { subject.download(pinId, ctx(attempt = 1, max = 3)) }
        verify { store.discard(staged()) }
        verify { store.delete(any()) }
        verify { downloads.recordLastError(pinId, "INTERNAL_ERROR", now) }
        verify(exactly = 0) { mediaRepository.save(any()) }
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test
    fun `Given the rollback delete throws, Then the task fails with the original cause`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        val promoteError = RuntimeException("disk full")
        every { store.promote(any(), any()) } throws promoteError
        every { store.delete(any()) } throws RuntimeException("cleanup boom")

        val thrown = assertThrows(RuntimeException::class.java) {
            subject.download(pinId, ctx(attempt = 1, max = 3))
        }

        // The cleanup exception must not mask the original promote failure; the retry policy
        // records the original cause and rethrows it.
        assertEquals(promoteError, thrown)
        verify { downloads.recordLastError(pinId, "disk full", now) }
    }

    @Test
    fun `Given the rollback discard throws, Then the task fails with the original cause`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        val promoteError = RuntimeException("disk full")
        every { store.promote(any(), any()) } throws promoteError
        every { store.discard(staged()) } throws RuntimeException("discard boom")

        val thrown = assertThrows(RuntimeException::class.java) {
            subject.download(pinId, ctx(attempt = 1, max = 3))
        }

        // The staged-temp discard failure must not mask the original promote failure; the retry
        // policy records the original cause and rethrows it.
        assertEquals(promoteError, thrown)
        verify { downloads.recordLastError(pinId, "disk full", now) }
    }

    @Test
    fun `Given the no-op-swap delete throws, Then the task still succeeds`() {
        stubUntilStage()
        every { probe.probe(any(), any()) } returns ProbeResult(MediaFormat.PNG, 1, 1, animated = false)
        every { downloads.deleteIfPending(pinId) } returns 0
        every { runner.inTransaction<Boolean>(any()) } answers { firstArg<() -> Boolean>().invoke() }
        every { store.delete(any()) } throws RuntimeException("cleanup boom")

        assertDoesNotThrow { subject.download(pinId, ctx()) }

        // A no-op swap is a success; the cleanup failure must not turn it into a retryable failure.
        verify(exactly = 0) { downloads.markFailed(any(), any(), any()) }
        verify(exactly = 0) { downloads.recordLastError(any(), any(), any()) }
        verify(exactly = 0) { mediaRepository.save(any()) }
    }
}
