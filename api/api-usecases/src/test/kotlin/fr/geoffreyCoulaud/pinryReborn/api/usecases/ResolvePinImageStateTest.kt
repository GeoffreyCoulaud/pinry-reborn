package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Image
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImagePermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImagePinDoesNotExistError
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

class ResolvePinImageStateTest {
    private val pins: PinRepositoryInterface = mockk()
    private val images: ImageRepositoryInterface = mockk()
    private val downloads: ImageDownloadRepositoryInterface = mockk()
    private val connection = SingleConnectionRunner()
    private val owner = User(randomUUID(), "o", createdAt = TestTime.now)
    private val pinId = randomUUID()
    private val subject = ResolvePinImageState(pins, images, downloads, connection)

    @Test fun `Given a missing pin, Then it throws ImagePinDoesNotExistError`() {
        every { pins.findPinById(pinId) } returns null
        assertThrows(ImagePinDoesNotExistError::class.java) { subject.resolve(pinId, owner) }
    }

    @Test fun `Given a non-owner, Then it throws ImagePermissionError`() {
        val otherUser = User(randomUUID(), "x", createdAt = TestTime.now)
        every { pins.findPinById(pinId) } returns Pin(pinId, otherUser, "c", null, "d", emptyList(), emptyList(),
            createdAt = TestTime.now, updatedAt = TestTime.now)
        assertThrows(ImagePermissionError::class.java) { subject.resolve(pinId, owner) }
    }

    @Test fun `Given an owner with no image and no download, Then NONE`() {
        every { pins.findPinById(pinId) } returns Pin(pinId, owner, "c", null, "d", emptyList(), emptyList(),
            createdAt = TestTime.now, updatedAt = TestTime.now)
        every { images.findByPinId(pinId) } returns null
        every { downloads.findByPinId(pinId) } returns null
        assertEquals(PinImageStatus.NONE, subject.resolve(pinId, owner).status)
    }

    @Test fun `Given a swap landing between the two reads, Then the state is one snapshot, not a torn pair`() {
        // Given: a READY jpeg with a PENDING replacement, and a writer that commits the swap the
        // moment the image was read: the new image saved and the replacement removed, together
        every { pins.findPinById(pinId) } returns Pin(pinId, owner, "c", null, "d", emptyList(), emptyList(),
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val oldImage = image(pinId, "image/jpeg")
        val newImage = image(pinId, "image/png")
        val pending = ImageDownload(pinId, "https://example.com/new.png", DownloadStatus.PENDING, null, null,
            randomUUID(), TestTime.now, TestTime.now)
        var swapped = false
        every { images.findByPinId(pinId) } answers {
            val seen = if (swapped) newImage else oldImage
            connection.write { swapped = true }
            seen
        }
        every { downloads.findByPinId(pinId) } answers { if (swapped) null else pending }

        // When
        val state = subject.resolve(pinId, owner)

        // Then: the old image with its replacement, or the new one without; never the old one alone
        val consistent =
            (state.image == oldImage && state.replacement?.status == DownloadStatus.PENDING) ||
                (state.image == newImage && state.replacement == null)
        assertTrue(consistent) { "torn read: image ${state.image?.mimeType}, replacement ${state.replacement}" }
    }

    @Test fun `Given a page of pins, Then statesFor reads each repository once, not once per pin`() {
        // Given: a page of three pins, one imaged, one downloading, one with neither
        val imaged = pin()
        val downloading = pin()
        val bare = pin()
        val stored = image(imaged.id, "image/png")
        val pending = ImageDownload(downloading.id, "https://example.com/i.png", DownloadStatus.PENDING, null, null,
            randomUUID(), TestTime.now, TestTime.now)
        val countedImages = CountingImages(mapOf(imaged.id to stored))
        val countedDownloads = CountingDownloads(mapOf(downloading.id to pending))
        val batching = ResolvePinImageState(pins, countedImages, countedDownloads, connection)

        // When
        val states = batching.statesFor(listOf(imaged, downloading, bare))

        // Then
        assertEquals(1, countedImages.calls)
        assertEquals(1, countedDownloads.calls)
        assertEquals(PinImageStatus.READY, states[imaged.id]?.status)
        assertEquals(stored, states[imaged.id]?.image)
        assertEquals(PinImageStatus.PENDING, states[downloading.id]?.status)
        assertNull(states[bare.id])
    }

    @Test fun `Given no pins, Then statesFor reads neither repository`() {
        // Given
        val countedImages = CountingImages(emptyMap())
        val countedDownloads = CountingDownloads(emptyMap())
        val batching = ResolvePinImageState(pins, countedImages, countedDownloads, connection)

        // When
        val states = batching.statesFor(emptyList())

        // Then
        assertTrue(states.isEmpty())
        assertEquals(0, countedImages.calls)
        assertEquals(0, countedDownloads.calls)
    }

    private fun pin() = Pin(randomUUID(), owner, "c", null, "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)

    private fun image(pinId: UUID, mimeType: String) =
        Image(randomUUID(), pinId, mimeType, 1, 1, false, 1L, "h-$mimeType", "k-$mimeType", TestTime.now)

    /** Counts the reads a page costs: the criterion is one per page, which a mock's `verify` cannot state. */
    private class CountingImages(private val stored: Map<UUID, Image>) : ImageRepositoryInterface {
        var calls = 0
            private set

        override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, Image> {
            calls++
            return stored.filterKeys { it in pinIds }
        }

        override fun save(image: Image): Image = error("not used")
        override fun findByPinId(pinId: UUID): Image? = error("not used")
        override fun deleteByPinId(pinId: UUID) = error("not used")
        override fun findMissingImageIds(candidates: Collection<UUID>): Set<UUID> = error("not used")
    }

    /** The download half of the same count. */
    private class CountingDownloads(
        private val stored: Map<UUID, ImageDownload>,
    ) : ImageDownloadRepositoryInterface {
        var calls = 0
            private set

        override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, ImageDownload> {
            calls++
            return stored.filterKeys { it in pinIds }
        }

        override fun upsertPending(pinId: UUID, sourceUrl: String, taskId: UUID, now: Instant): ImageDownload =
            error("not used")

        override fun findByPinId(pinId: UUID): ImageDownload? = error("not used")
        override fun findByAuthor(authorId: UUID): List<ImageDownload> = error("not used")
        override fun findByAuthorAndPin(authorId: UUID, pinId: UUID): ImageDownload? = error("not used")
        override fun markFailed(pinId: UUID, reason: DownloadReason, now: Instant): Boolean = error("not used")
        override fun recordLastError(pinId: UUID, lastError: String, now: Instant): Boolean = error("not used")
        override fun deleteIfPending(pinId: UUID): Int = error("not used")
        override fun deleteByPinId(pinId: UUID) = error("not used")
        override fun deleteFailedBefore(cutoff: Instant): Int = error("not used")

        override fun failPendingBefore(cutoff: Instant, reason: DownloadReason, now: Instant): Int =
            error("not used")
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
