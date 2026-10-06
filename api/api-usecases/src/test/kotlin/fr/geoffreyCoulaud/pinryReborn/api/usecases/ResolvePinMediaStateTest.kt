package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class ResolvePinMediaStateTest {
    private val pins: PinRepositoryInterface = mockk()
    private val mediaRepository: MediaRepositoryInterface = mockk()
    private val downloads: MediaDownloadRepositoryInterface = mockk()
    private val connection = SingleConnectionRunner()
    private val owner = User(randomUUID(), "o", createdAt = TestTime.now)
    private val pinId = randomUUID()
    private val subject = ResolvePinMediaState(pins, mediaRepository, downloads, connection)

    @Test fun `Given a missing pin, Then it throws MediaPinDoesNotExistError`() {
        every { pins.findPinById(pinId) } returns null
        assertThrows(MediaPinDoesNotExistError::class.java) { subject.resolve(pinId, owner) }
    }

    @Test fun `Given a non-owner, Then it throws MediaPermissionError`() {
        val otherUser = User(randomUUID(), "x", createdAt = TestTime.now)
        every { pins.findPinById(pinId) } returns Pin(pinId, otherUser, "c", null, "d", emptyList(), emptyList(),
            createdAt = TestTime.now, updatedAt = TestTime.now)
        assertThrows(MediaPermissionError::class.java) { subject.resolve(pinId, owner) }
    }

    @Test fun `Given an owner with no image and no download, Then NONE`() {
        every { pins.findPinById(pinId) } returns Pin(pinId, owner, "c", null, "d", emptyList(), emptyList(),
            createdAt = TestTime.now, updatedAt = TestTime.now)
        every { mediaRepository.findByPinId(pinId) } returns null
        every { downloads.findByPinId(pinId) } returns null
        assertEquals(PinMediaStatus.NONE, subject.resolve(pinId, owner).status)
    }

    @Test fun `Given a swap landing between the two reads, Then the state is one snapshot, not a torn pair`() {
        // Given: a READY jpeg with a PENDING replacement, and a writer that commits the swap the
        // moment the image was read: the new image saved and the replacement removed, together
        every { pins.findPinById(pinId) } returns Pin(pinId, owner, "c", null, "d", emptyList(), emptyList(),
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val oldMedia = media(pinId, "image/jpeg")
        val newMedia = media(pinId, "image/png")
        val pending = MediaDownload(pinId, "https://example.com/new.png", DownloadStatus.PENDING, null, null,
            randomUUID(), TestTime.now, TestTime.now)
        var swapped = false
        every { mediaRepository.findByPinId(pinId) } answers {
            val seen = if (swapped) newMedia else oldMedia
            connection.write { swapped = true }
            seen
        }
        every { downloads.findByPinId(pinId) } answers { if (swapped) null else pending }

        // When
        val state = subject.resolve(pinId, owner)

        // Then: the old image with its replacement, or the new one without; never the old one alone
        val consistent =
            (state.media == oldMedia && state.replacement?.status == DownloadStatus.PENDING) ||
                (state.media == newMedia && state.replacement == null)
        assertTrue(consistent) { "torn read: image ${state.media?.mimeType}, replacement ${state.replacement}" }
    }

    @Test fun `Given a page of pins, Then statesFor reads each repository once, not once per pin`() {
        // Given: a page of three pins, one imaged, one downloading, one with neither
        val imaged = pin()
        val downloading = pin()
        val bare = pin()
        val stored = media(imaged.id, "image/png")
        val pending = MediaDownload(downloading.id, "https://example.com/i.png", DownloadStatus.PENDING, null, null,
            randomUUID(), TestTime.now, TestTime.now)
        val countedMedia = CountingMedia(mapOf(imaged.id to stored))
        val countedDownloads = CountingDownloads(mapOf(downloading.id to pending))
        val batching = ResolvePinMediaState(pins, countedMedia, countedDownloads, connection)

        // When
        val states = batching.statesFor(listOf(imaged, downloading, bare))

        // Then
        assertEquals(1, countedMedia.calls)
        assertEquals(1, countedDownloads.calls)
        assertEquals(PinMediaStatus.READY, states[imaged.id]?.status)
        assertEquals(stored, states[imaged.id]?.media)
        assertEquals(PinMediaStatus.PENDING, states[downloading.id]?.status)
        assertNull(states[bare.id])
    }

    @Test fun `Given no pins, Then statesFor reads neither repository`() {
        // Given
        val countedMedia = CountingMedia(emptyMap())
        val countedDownloads = CountingDownloads(emptyMap())
        val batching = ResolvePinMediaState(pins, countedMedia, countedDownloads, connection)

        // When
        val states = batching.statesFor(emptyList())

        // Then
        assertTrue(states.isEmpty())
        assertEquals(0, countedMedia.calls)
        assertEquals(0, countedDownloads.calls)
    }

    private fun pin() = Pin(randomUUID(), owner, "c", null, "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)

    private fun media(pinId: UUID, mimeType: String) =
        Media.StillImage(randomUUID(), pinId, mimeType, 1, 1, 1L,"h-$mimeType", "k-$mimeType", TestTime.now)

    /** Counts the reads a page costs: the criterion is one per page, which a mock's `verify` cannot state. */
    private class CountingMedia(private val stored: Map<UUID, Media>) : MediaRepositoryInterface {
        var calls = 0
            private set

        override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, Media> {
            calls++
            return stored.filterKeys { it in pinIds }
        }

        override fun save(media: Media): Media = error("not used")
        override fun findByPinId(pinId: UUID): Media? = error("not used")
        override fun deleteByPinId(pinId: UUID) = error("not used")
        override fun findMissingMediaIds(candidates: Collection<UUID>): Set<UUID> = error("not used")
        override fun findNewestNotFingerprinted(version: Int): Media? = error("not used")
        override fun markFingerprinted(mediaId: UUID, version: Int) = error("not used")
        override fun findComparable(media: Media, candidates: Collection<UUID>, version: Int): List<Media> =
            error("not used")
    }

    /** The download half of the same count. */
    private class CountingDownloads(
        private val stored: Map<UUID, MediaDownload>,
    ) : MediaDownloadRepositoryInterface {
        var calls = 0
            private set

        override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, MediaDownload> {
            calls++
            return stored.filterKeys { it in pinIds }
        }

        override fun upsertPending(pinId: UUID, sourceUrl: String, taskId: UUID, now: Instant): MediaDownload =
            error("not used")

        override fun findByPinId(pinId: UUID): MediaDownload? = error("not used")
        override fun findByAuthor(authorId: UUID, cursor: Cursor?, pageSize: Int): Page<MediaDownload> =
            error("not used")

        override fun findByAuthorAndPin(authorId: UUID, pinId: UUID): MediaDownload? = error("not used")
        override fun markFailed(pinId: UUID, reason: DownloadReason, now: Instant): Boolean = error("not used")
        override fun recordLastError(pinId: UUID, lastError: String, now: Instant): Boolean = error("not used")
        override fun deleteIfPending(pinId: UUID): Int = error("not used")
        override fun deleteByPinId(pinId: UUID) = error("not used")
        override fun deleteFailedBefore(cutoff: Instant): Int = error("not used")
        override fun findPending(): List<MediaDownload> = error("not used")
    }

    /** The single connection's pool: a write queued while a transaction holds it lands when that ends. */
    private class SingleConnectionRunner : TransactionRunner {
        private var held = false
        private val queued = mutableListOf<() -> Unit>()

        fun write(commit: () -> Unit) {
            if (held) queued += commit else commit()
        }

        override fun <T> inTransaction(block: () -> T): T {
            held = true
            try {
                return block()
            } finally {
                held = false
                queued.forEach { it() }
                queued.clear()
            }
        }
    }
}
