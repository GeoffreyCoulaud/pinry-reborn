package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaCodecUnsupportedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaInvalidError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaTooLargeError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaTooLongError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID

class SetPinMediaTest : BaseTest() {
    private val pins = mockk<PinRepositoryInterface>()
    private val mediaRepository = mockk<MediaRepositoryInterface>(relaxed = true)
    private val store = mockk<MediaStore>(relaxed = true)
    private val probe = mockk<ImageProbe>()
    private val clock = mockk<Clock>()
    private val clearPinDownload = mockk<ClearPinDownload>(relaxed = true)
    private val renditionCache = mockk<RenditionCache>()
    private val bounds = MediaBounds(maxImageBytes = 30, maxVideoBytes = 0, Duration.ZERO, maxPixels = 50)
    private val ingestion = MediaIngestion(store, probe, NoVideoProcessor, bounds)
    private val useCase = SetPinMedia(pins, mediaRepository, store, ingestion, clock, clearPinDownload, renditionCache)

    private val owner = User(randomUUID(), createRandomString(), createdAt = TestTime.now)
    private fun pin(author: User = owner) = Pin(randomUUID(), author, "https://c", null, "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)
    private fun upload() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
    private val staged = StagedFile("/tmp/s", 3, "hash")

    init { every { renditionCache.evictMedia(any()) } returns Unit }

    @Test fun `Given a valid upload by the owner, Then it stores and persists a new image`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clock.now() } returns Instant.parse("2026-07-08T00:00:00Z")
        every { mediaRepository.save(any()) } answers { firstArg() }

        val result = useCase.set(p.id, owner, upload())

        assertEquals(p.id, result.media.pinId)
        assertEquals("image/png", result.media.mimeType)
        assertTrue(result.media.storageKey.startsWith("originals/${owner.id}/${p.id}/"))
        assertFalse(result.replaced)
        verify { store.promote(staged, result.media.storageKey) }
        verify { mediaRepository.save(result.media) }
        verify { clearPinDownload.clear(p.id) }
        // A first-time upload has no superseded image, so nothing is evicted.
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test fun `Given a replacement, Then the old file is deleted after commit`() {
        val p = pin()
        val old = Media(randomUUID(), p.id, "image/png", 1, 1, false, 1, "old", "originals/o/old.png", Instant.EPOCH)
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.WEBP, 2, 2, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns old
        every { clock.now() } returns Instant.EPOCH
        every { mediaRepository.save(any()) } answers { firstArg() }

        val result = useCase.set(p.id, owner, upload())

        assertTrue(result.replaced)
        verify { store.delete("originals/o/old.png") }
    }

    @Test fun `Given a replaced image, Then the old image's rendition cache is evicted`() {
        val p = pin()
        val old = Media(randomUUID(), p.id, "image/png", 1, 1, false, 1, "old", "originals/o/old.png", Instant.EPOCH)
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.WEBP, 2, 2, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns old
        every { clock.now() } returns Instant.EPOCH
        every { mediaRepository.save(any()) } answers { firstArg() }

        useCase.set(p.id, owner, upload())

        verify { renditionCache.evictMedia(old.id) }
    }

    @Test fun `Given the rendition cache eviction fails during replace, Then the upload still succeeds`() {
        val p = pin()
        val old = Media(randomUUID(), p.id, "image/png", 1, 1, false, 1, "old", "originals/o/old.png", Instant.EPOCH)
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.WEBP, 2, 2, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns old
        every { clock.now() } returns Instant.EPOCH
        every { mediaRepository.save(any()) } answers { firstArg() }
        every { renditionCache.evictMedia(any()) } throws RuntimeException("io")

        val result = useCase.set(p.id, owner, upload())

        assertTrue(result.replaced)
        verify { mediaRepository.save(result.media) }
    }

    @Test fun `Given a missing pin, Then it throws MediaPinDoesNotExistError`() {
        every { pins.findPinById(any()) } returns null
        assertThrows(MediaPinDoesNotExistError::class.java) { useCase.set(randomUUID(), owner, upload()) }
    }

    @Test fun `Given a non-owner, Then it throws MediaPermissionError`() {
        val p = pin(author = User(randomUUID(), createRandomString(), createdAt = TestTime.now))
        every { pins.findPinById(p.id) } returns p
        assertThrows(MediaPermissionError::class.java) { useCase.set(p.id, owner, upload()) }
    }

    @Test fun `Given an oversize upload, Then it throws MediaTooLargeError`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } throws MediaTooLargeException("too big")
        assertThrows(MediaTooLargeError::class.java) { useCase.set(p.id, owner, upload()) }
    }

    @Test fun `Given an image past its byte bound once probed, Then it throws MediaTooLargeError`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns StagedFile("/tmp/s", 31, "hash")
        every { probe.probe(any(), 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { clock.now() } returns Instant.EPOCH
        assertThrows(MediaTooLargeError::class.java) { useCase.set(p.id, owner, upload()) }
    }

    @Test fun `Given a format libvips reads and the server refuses, Then it throws MediaCodecUnsupportedError`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } throws UnsupportedImageFormatException("heifload")
        every { clock.now() } returns Instant.EPOCH
        assertThrows(MediaCodecUnsupportedError::class.java) { useCase.set(p.id, owner, upload()) }
    }

    @Test fun `Given an image format the server refuses, Then the error's message names no libvips loader`() {
        // Given
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } throws UnsupportedImageFormatException("Unsupported image loader: tiffload")
        every { clock.now() } returns Instant.EPOCH

        // When
        val error = assertThrows(MediaCodecUnsupportedError::class.java) { useCase.set(p.id, owner, upload()) }

        // Then
        assertEquals("The image format is not accepted", error.message)
    }

    @Test fun `Given an undecodable image and a video ffmpeg cannot repackage, Then both errors carry one message`() {
        // Given
        val p = pin()
        val video = mockk<VideoProcessor>()
        val videoBounds = MediaBounds(maxImageBytes = 30, maxVideoBytes = 30, Duration.ofSeconds(1), maxPixels = 50)
        val withVideo = SetPinMedia(
            pins, mediaRepository, store, MediaIngestion(store, probe, video, videoBounds), clock, clearPinDownload,
            renditionCache,
        )
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } throws UndecodableImageException("not an image")
        every { clock.now() } returns Instant.EPOCH
        every { video.probe(staged, Duration.ofSeconds(1)) } returns
            VideoProbeResult(
                VideoCodec.H264, null, 2, 2, Duration.ofSeconds(1), "avc1.640015", VideoContainer.MP4,
                alreadyRepackaged = true,
            )
        every { video.repackage(staged, any()) } throws UndecodableVideoException("refused")

        // When
        val imageError = assertThrows(MediaInvalidError::class.java) { useCase.set(p.id, owner, upload()) }
        val videoError = assertThrows(MediaInvalidError::class.java) { withVideo.set(p.id, owner, upload()) }

        // Then
        assertEquals(imageError.message, videoError.message)
    }

    @Test fun `Given a video the processor refuses, Then each refusal takes its own error`() {
        // Given
        val p = pin()
        val video = mockk<VideoProcessor>()
        val videoBounds = MediaBounds(maxImageBytes = 30, maxVideoBytes = 30, Duration.ofSeconds(1), maxPixels = 50)
        val withVideo = SetPinMedia(
            pins, mediaRepository, store, MediaIngestion(store, probe, video, videoBounds), clock, clearPinDownload,
            renditionCache,
        )
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } throws UndecodableImageException("not an image")
        every { clock.now() } returns Instant.EPOCH
        val refusals = mapOf(
            VideoCodecUnsupportedException("ac3") to MediaCodecUnsupportedError::class.java,
            VideoTooLongException("121 s") to MediaTooLongError::class.java,
            VideoProcessorTimeoutException("ffprobe") to VideoProcessorTimeoutException::class.java,
        )
        for ((refusal, expected) in refusals) {
            every { video.probe(staged, Duration.ofSeconds(1)) } throws refusal
            // When / Then
            assertThrows(expected) { withVideo.set(p.id, owner, upload()) }
        }
        // A file ffprobe reads and ffmpeg then refuses
        every { video.probe(staged, Duration.ofSeconds(1)) } returns
            VideoProbeResult(
                VideoCodec.H264, null, 2, 2, Duration.ofSeconds(1), "avc1.640015", VideoContainer.MP4,
                alreadyRepackaged = true,
            )
        every { video.repackage(staged, any()) } throws UndecodableVideoException("refused")
        assertThrows(MediaInvalidError::class.java) { withVideo.set(p.id, owner, upload()) }
    }

    @Test fun `Given an undecodable upload, Then it discards the temp and throws MediaInvalidError`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } throws UndecodableImageException("nope")
        every { clock.now() } returns Instant.EPOCH
        assertThrows(MediaInvalidError::class.java) { useCase.set(p.id, owner, upload()) }
        verify { store.discard(staged) }
    }

    @Test fun `Given a promote failure, Then it discards the temp and rethrows`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clock.now() } returns Instant.EPOCH
        every { store.promote(any(), any()) } throws RuntimeException("disk full")

        assertThrows(RuntimeException::class.java) { useCase.set(p.id, owner, upload()) }
        verify { store.discard(staged) }
        verify(exactly = 0) { mediaRepository.save(any()) }
    }

    @Test fun `Given an IO failure during promote, Then it discards the temp and rethrows`() {
        // FilesystemMediaStore.promote throws java.io.IOException (Files.createDirectories /
        // Files.move), not a RuntimeException; the cleanup catch must cover it too.
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clock.now() } returns Instant.EPOCH
        every { store.promote(any(), any()) } throws IOException("disk full")

        assertThrows(IOException::class.java) { useCase.set(p.id, owner, upload()) }
        verify { store.discard(staged) }
        verify(exactly = 0) { mediaRepository.save(any()) }
    }

    @Test fun `Given save fails after a successful promote, Then it discards the temp and deletes the promoted file`() {
        val p = pin()
        val storageKeySlot = slot<String>()
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clock.now() } returns Instant.EPOCH
        every { store.promote(staged, capture(storageKeySlot)) } just runs
        every { mediaRepository.save(any()) } throws RuntimeException("db down")

        assertThrows(RuntimeException::class.java) { useCase.set(p.id, owner, upload()) }

        verify { store.discard(staged) }
        verify { store.delete(storageKeySlot.captured) }
    }

    @Test fun `Given the old file delete fails during replace, Then the request still succeeds`() {
        val p = pin()
        val old = Media(randomUUID(), p.id, "image/png", 1, 1, false, 1, "old", "originals/o/old.png", Instant.EPOCH)
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.WEBP, 2, 2, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns old
        every { clock.now() } returns Instant.EPOCH
        every { mediaRepository.save(any()) } answers { firstArg() }
        every { store.delete("originals/o/old.png") } throws RuntimeException("locked")

        val result = useCase.set(p.id, owner, upload())

        assertTrue(result.replaced)
    }

    @Test fun `Given the rollback delete throws, Then the original promote error is preserved`() {
        val p = pin()
        val promoteError = RuntimeException("disk full")
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clock.now() } returns Instant.EPOCH
        every { store.promote(any(), any()) } throws promoteError
        every { store.delete(any()) } throws RuntimeException("cleanup boom")

        val thrown = assertThrows(RuntimeException::class.java) { useCase.set(p.id, owner, upload()) }

        // The cleanup exception must not mask the original promote failure.
        assertEquals(promoteError, thrown)
        verify { store.discard(staged) }
    }

    @Test fun `Given the rollback discard throws, Then the original promote error is preserved`() {
        val p = pin()
        val promoteError = RuntimeException("disk full")
        every { pins.findPinById(p.id) } returns p
        every { store.stage(any(), 30) } returns staged
        every { probe.probe(staged, 50) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clock.now() } returns Instant.EPOCH
        every { store.promote(any(), any()) } throws promoteError
        every { store.discard(staged) } throws RuntimeException("discard boom")

        val thrown = assertThrows(RuntimeException::class.java) { useCase.set(p.id, owner, upload()) }

        // The staged-temp discard failure must not mask the original promote error.
        assertEquals(promoteError, thrown)
    }
}
