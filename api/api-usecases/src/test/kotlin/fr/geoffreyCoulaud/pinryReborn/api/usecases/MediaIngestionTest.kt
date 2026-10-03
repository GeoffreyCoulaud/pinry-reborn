package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID.randomUUID

class MediaIngestionTest : BaseTest() {
    private val store = mockk<MediaStore>(relaxed = true)
    private val probe = mockk<ImageProbe>()
    private val maxPixels = 50L
    private val bounds = MediaBounds(maxImageBytes = 30, maxVideoBytes = 40, maxPixels = maxPixels)
    private val ingestion = MediaIngestion(store, probe, bounds)

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
        every { probe.probe(heavy, maxPixels) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)

        // When
        assertThrows(MediaTooLargeException::class.java) { ingestion.ingest(heavy, ownerId, pinId, createdAt) }

        // Then
        verify { store.discard(heavy) }
    }

    @Test fun `Given a decodable file, Then the row carries the probe's answer under its owner's and pin's key`() {
        // Given
        every { probe.probe(staged, maxPixels) } returns ProbeResult(MediaFormat.WEBP, 4, 5, animated = true)

        // When
        val media = ingestion.ingest(staged, ownerId, pinId, createdAt).media

        // Then
        assertEquals("originals/$ownerId/$pinId/${media.id}.webp", media.storageKey)
        assertEquals("image/webp", media.mimeType)
        assertEquals(4, media.width)
        assertEquals(5, media.height)
        assertEquals(true, media.animated)
        assertEquals(staged.byteSize, media.byteSize)
        assertEquals(staged.contentHash, media.contentHash)
        assertEquals(pinId, media.pinId)
        assertEquals(createdAt, media.createdAt)
    }

    @Test fun `Given a refused probe, Then the staged file is discarded and the refusal rethrown`() {
        // Given
        val refusal = UndecodableImageException("nope")
        every { probe.probe(staged, maxPixels) } throws refusal

        // When
        val thrown = assertThrows(UndecodableImageException::class.java) {
            ingestion.ingest(staged, ownerId, pinId, createdAt)
        }

        // Then
        assertEquals(refusal, thrown)
        verify { store.discard(staged) }
    }

    @Test fun `Given an ingested media, Then promote moves it to its key and discard removes both copies`() {
        // Given
        every { probe.probe(staged, maxPixels) } returns ProbeResult(MediaFormat.PNG, 4, 5, animated = false)
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
