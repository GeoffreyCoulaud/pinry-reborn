package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTooManyPixelsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID

class MediaIngestionTest : BaseTest() {
    private val store = mockk<MediaStore>(relaxed = true)
    private val probe = mockk<ImageProbe>()
    private val video = mockk<VideoProcessor>()
    private val maxDuration = Duration.ofSeconds(120)
    private val limits = MediaLimits(maxImageBytes = 30, maxVideoBytes = 40, maxDuration, 50, maxPixelsPerRender = 50)
    private val ingestion = MediaIngestion(store, probe, video, limits)
    private val aVideo = VideoProbeResult(
        VideoCodec.VP9, AudioCodec.OPUS, 4, 6, Duration.ofSeconds(1), frames = 25, bytes = 3, "vp09.00.10.08,opus",
        VideoContainer.WEBM, alreadyRepackaged = true,
    )
    private val anMp4 = aVideo.copy(
        videoCodec = VideoCodec.H264, audioCodec = null, codecs = "avc1.640015", demuxedAs = VideoContainer.MP4,
    )

    private val ownerId = randomUUID()
    private val pinId = randomUUID()
    private val createdAt = Instant.parse("2026-10-03T00:00:00Z")
    private val staged = StagedFile("/tmp/s", 3, "hash")

    @Test fun `Given a source, Then it is staged and digested under the larger byte bound`() {
        // Given
        val source = ByteArrayInputStream(byteArrayOf(1))
        every { store.stage(source, 40) } returns staged
        every { store.digest(source, 40) } returns "digest"

        // When
        val stagedFile = ingestion.stage(source)
        val digest = ingestion.digest(source)

        // Then
        assertEquals(staged, stagedFile)
        assertEquals("digest", digest)
    }

    @Test fun `Given an image past its own byte bound but under the video's, Then it is discarded and refused`() {
        // Given
        val heavy = StagedFile("/tmp/heavy", 35, "hash")
        every { probe.probe(heavy) } returns ProbeResult(MediaFormat.PNG, 4, 5, frames = 1, heavy.byteSize)

        // When
        assertThrows(MediaTooLargeException::class.java) { ingestion.ingest(heavy, ownerId, pinId, createdAt) }

        // Then
        verify { store.discard(heavy) }
    }

    @Test fun `Given an image past the per-frame bound, Then it is discarded and refused`() {
        // Given: 10 by 6 is 60 pixels, past the bound of 50
        every { probe.probe(staged) } returns ProbeResult(MediaFormat.PNG, 10, 6, frames = 1, staged.byteSize)

        // When
        assertThrows(ImageTooManyPixelsException::class.java) { ingestion.ingest(staged, ownerId, pinId, createdAt) }

        // Then
        verify { store.discard(staged) }
    }

    @Test fun `Given a decodable file, Then the row carries the probe's answer under its owner's and pin's key`() {
        // Given
        every { probe.probe(staged) } returns ProbeResult(MediaFormat.WEBP, 4, 5, frames = 3, staged.byteSize)

        // When
        val media = ingestion.ingest(staged, ownerId, pinId, createdAt).media

        // Then
        assertEquals("originals/$ownerId/$pinId/${media.id}.webp", media.storageKey)
        assertEquals("image/webp", media.mimeType)
        assertEquals(4, media.width)
        assertEquals(5, media.height)
        assertEquals(true, media.animated)
        assertEquals(3, media.frames)
        assertEquals(null, media.duration)
        assertEquals(staged.byteSize, media.byteSize)
        assertEquals(staged.contentHash, media.contentHash)
        assertEquals(pinId, media.pinId)
        assertEquals(createdAt, media.createdAt)
    }

    @Test fun `Given a file neither probe reads, Then the staged file is discarded and the image refusal rethrown`() {
        // Given
        val refusal = UndecodableImageException("nope")
        every { probe.probe(staged) } throws refusal
        every { video.probe(staged, maxDuration) } throws UndecodableVideoException("no video track")

        // When
        val thrown = assertThrows(UndecodableImageException::class.java) {
            ingestion.ingest(staged, ownerId, pinId, createdAt)
        }

        // Then
        assertEquals(refusal, thrown)
        verify { store.discard(staged) }
    }

    @Test fun `Given a format libvips reads and ffprobe refuses, Then libvips' refusal is rethrown`() {
        // Given: an AVIF, which libvips opens and the enum lacks, and which holds a single frame
        val refusal = UnsupportedImageFormatException("heifload")
        every { probe.probe(staged) } throws refusal
        every { video.probe(staged, maxDuration) } throws UndecodableVideoException("single frame")

        // When
        val thrown = assertThrows(UnsupportedImageFormatException::class.java) {
            ingestion.ingest(staged, ownerId, pinId, createdAt)
        }

        // Then
        assertEquals(refusal, thrown)
        verify { store.discard(staged) }
    }

    @Test fun `Given a video, Then the repackaged file is what the row and the key describe`() {
        // Given
        val repackaged = StagedFile("/tmp/r", 7, "repackaged")
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } returns aVideo
        every { video.repackage(staged, aVideo) } returns repackaged

        // When
        val ingested = ingestion.ingest(staged, ownerId, pinId, createdAt)

        // Then
        val media = ingested.media
        assertEquals(repackaged, ingested.staged)
        assertEquals("originals/$ownerId/$pinId/${media.id}.webm", media.storageKey)
        assertEquals("video/webm; codecs=\"vp09.00.10.08,opus\"", media.mimeType)
        assertEquals(4, media.width)
        assertEquals(6, media.height)
        assertEquals(true, media.animated)
        assertEquals(25, media.frames)
        assertEquals(Duration.ofSeconds(1), media.duration)
        assertEquals(7, media.byteSize)
        assertEquals("repackaged", media.contentHash)
        verify { store.discard(staged) }
    }

    @Test fun `Given an archived MP4 already repackaged, Then it is stored as the archive carries it`() {
        // Given: repackaged again, an MP4 would not keep its bytes
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } returns anMp4

        // When
        val ingested = ingestion.ingestArchived(staged, ownerId, pinId, createdAt)

        // Then
        assertEquals(staged, ingested.staged)
        assertEquals(staged.contentHash, ingested.media.contentHash)
        assertEquals("video/mp4; codecs=\"avc1.640015\"", ingested.media.mimeType)
        verify(exactly = 0) { video.repackage(any(), any()) }
    }

    @Test fun `Given an archived MP4 with a track or a tag repackaging drops, Then it is repackaged`() {
        // Given: an H.265 tagged hev1, say, which its stored type would call hvc1
        val handMade = anMp4.copy(alreadyRepackaged = false)
        val repackaged = StagedFile("/tmp/r", 7, "repackaged")
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } returns handMade
        every { video.repackage(staged, handMade) } returns repackaged

        // When
        val ingested = ingestion.ingestArchived(staged, ownerId, pinId, createdAt)

        // Then
        assertEquals(repackaged, ingested.staged)
    }

    @Test fun `Given a video past the pixel bound, Then it is discarded and refused before repackaging`() {
        // Given: 10 by 6 is 60 pixels, past the bound of 50
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } returns aVideo.copy(width = 10, height = 6)

        // When
        assertThrows(ImageTooManyPixelsException::class.java) { ingestion.ingest(staged, ownerId, pinId, createdAt) }

        // Then
        verify { store.discard(staged) }
        verify(exactly = 0) { video.repackage(any(), any()) }
    }

    @Test fun `Given an archived WebM, Then it is repackaged, which keeps a stored WebM's bytes`() {
        // Given: one demuxer reads WebM and Matroska alike, so only a repackaging makes it a real WebM
        val repackaged = StagedFile("/tmp/r", 7, "repackaged")
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } returns aVideo
        every { video.repackage(staged, aVideo) } returns repackaged

        // When
        val ingested = ingestion.ingestArchived(staged, ownerId, pinId, createdAt)

        // Then
        assertEquals(repackaged, ingested.staged)
    }

    @Test fun `Given an archived video outside the container its codecs choose, Then it is repackaged`() {
        // Given: VP9 with Opus found in an MP4, where its codecs choose WebM
        val misplaced = aVideo.copy(demuxedAs = VideoContainer.MP4)
        val repackaged = StagedFile("/tmp/r", 7, "repackaged")
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } returns misplaced
        every { video.repackage(staged, misplaced) } returns repackaged

        // When
        val ingested = ingestion.ingestArchived(staged, ownerId, pinId, createdAt)

        // Then
        assertEquals(repackaged, ingested.staged)
        assertEquals("repackaged", ingested.media.contentHash)
        verify { store.discard(staged) }
    }

    @Test fun `Given a video past its byte bound, Then it is discarded and refused before repackaging`() {
        // Given
        val heavy = StagedFile("/tmp/heavy", 41, "hash")
        every { probe.probe(heavy) } throws UndecodableImageException("not an image")
        every { video.probe(heavy, maxDuration) } returns aVideo.copy(bytes = heavy.byteSize)

        // When
        assertThrows(MediaTooLargeException::class.java) { ingestion.ingest(heavy, ownerId, pinId, createdAt) }

        // Then
        verify { store.discard(heavy) }
        verify(exactly = 0) { video.repackage(any(), any()) }
    }

    @Test fun `Given a video the probe refuses on its merits, Then that refusal is rethrown`() {
        // Given
        every { probe.probe(staged) } throws UndecodableImageException("not an image")
        every { video.probe(staged, maxDuration) } throws VideoTooLongException("121 s")

        // When / Then
        assertThrows(VideoTooLongException::class.java) { ingestion.ingest(staged, ownerId, pinId, createdAt) }
        verify { store.discard(staged) }
    }

    @Test fun `Given an ingested media, Then promote moves it to its key and discard removes both copies`() {
        // Given
        every { probe.probe(staged) } returns ProbeResult(MediaFormat.PNG, 4, 5, frames = 1, staged.byteSize)
        val ingested = ingestion.ingest(staged, ownerId, pinId, createdAt)

        // When
        ingestion.promote(ingested)
        ingestion.discard(ingested)

        // Then
        verify { store.promote(staged, ingested.media.storageKey) }
        verify { store.discard(staged) }
        verify { store.delete(ingested.media.storageKey) }
    }
}

/** For the suites about images: no file is a video, so a refused image keeps libvips' refusal. */
internal object NoVideoProcessor : VideoProcessor {
    override fun probe(staged: StagedFile, maxDuration: Duration): VideoProbeResult =
        throw UndecodableVideoException("not a video")

    override fun repackage(staged: StagedFile, video: VideoProbeResult): StagedFile = error("never probed")

    override fun poster(staged: StagedFile, shortestSide: Int, fromOneFrame: Boolean): StagedFile =
        error("never probed")

    override fun preview(staged: StagedFile, shortestSide: Int): StagedFile = error("never probed")
}
