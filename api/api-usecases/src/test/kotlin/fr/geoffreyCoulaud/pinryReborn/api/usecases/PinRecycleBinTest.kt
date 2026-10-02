package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPinAlreadySoftDeletedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPinNotSoftDeletedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.PassthroughTransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class PinRecycleBinTest {
    private val pinRepository = mockk<PinRepositoryInterface>()
    private val mediaRepository = mockk<MediaRepositoryInterface>(relaxed = true)
    private val mediaStore = mockk<MediaStore>(relaxed = true)
    private val clearPinDownload = mockk<ClearPinDownload>(relaxed = true)
    private val renditionCache = mockk<RenditionCache>()
    private val clock = mockk<Clock>()
    private val transitionInstant = Instant.parse("2026-07-29T08:30:00Z")
    private val useCase = PinRecycleBin(
        pinRepository = pinRepository,
        mediaRepository = mediaRepository,
        mediaStore = mediaStore,
        clearPinDownload = clearPinDownload,
        renditionCache = renditionCache,
        clock = clock,
        transactionRunner = PassthroughTransactionRunner(),
    )

    @BeforeEach
    fun stubClockAndRenditionCache() {
        every { renditionCache.evictMedia(any()) } returns Unit
        every { clock.now() } returns transitionInstant
    }

    private fun createPin(author: User, softDeletedAt: Instant? = null) = Pin(
        id = randomUUID(),
        author = author,
        sourceContextUrl = "https://example.com",
        sourceMediaUrl = "https://example.com/img.jpg",
        description = "A pin",
        tags = emptyList(),
        boards = emptyList(),
        softDeletedAt = softDeletedAt,
        createdAt = TestTime.now,
        updatedAt = TestTime.now,
    )

    private fun createMedia(pinId: UUID) = Media(
        id = randomUUID(),
        pinId = pinId,
        mimeType = "image/png",
        width = 1,
        height = 1,
        animated = false,
        byteSize = 1,
        contentHash = "hash",
        storageKey = "originals/x/$pinId/i.png",
        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
    )

    // --- Soft delete ---

    @Test
    fun `Given valid pin owned by user, Then soft delete succeeds and does not touch the image`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user)
        every { pinRepository.findPinById(pin.id) } returns pin
        every { pinRepository.softDeletePin(pin = pin, at = any()) } returns
            pin.copy(softDeletedAt = transitionInstant)

        // When
        useCase.softDelete(pinId = pin.id, user = user)

        // Then
        verify { pinRepository.softDeletePin(pin = pin, at = any()) }
        verify(exactly = 0) { mediaRepository.deleteByPinId(any()) }
        verify(exactly = 0) { mediaStore.delete(any()) }
    }

    @Test
    fun `Given an owned active pin, Then soft delete hands the repository the clock's instant`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user)
        val stampedInstant = slot<Instant>()
        every { pinRepository.findPinById(pin.id) } returns pin
        every { pinRepository.softDeletePin(pin = pin, at = any()) } returns
            pin.copy(softDeletedAt = transitionInstant)

        // When
        useCase.softDelete(pinId = pin.id, user = user)

        // Then
        verify { pinRepository.softDeletePin(pin = pin, at = capture(stampedInstant)) }
        assertEquals(transitionInstant, stampedInstant.captured)
    }

    @Test
    fun `Given already soft-deleted pin, Then soft delete throws PinDeletionPinAlreadySoftDeletedError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinDeletionPinAlreadySoftDeletedError> {
            useCase.softDelete(pinId = pin.id, user = user)
        }
    }

    @Test
    fun `Given pin not owned by user, Then soft delete throws PinDeletionPermissionError`() {
        // Given
        val owner = User(id = randomUUID(), name = "Owner", createdAt = TestTime.now)
        val otherUser = User(id = randomUUID(), name = "Other", createdAt = TestTime.now)
        val pin = createPin(author = owner)
        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinDeletionPermissionError> {
            useCase.softDelete(pinId = pin.id, user = otherUser)
        }
    }

    @Test
    fun `Given pin does not exist, Then soft delete throws PinDeletionPinDoesNotExistError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pinId = randomUUID()
        every { pinRepository.findPinById(pinId) } returns null

        // When, Then
        assertThrows<PinDeletionPinDoesNotExistError> {
            useCase.softDelete(pinId = pinId, user = user)
        }
    }

    // --- Restore ---

    @Test
    fun `Given soft-deleted pin owned by user, Then restore succeeds and does not touch the image`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        every { pinRepository.findPinById(pin.id) } returns pin
        every { pinRepository.restorePin(pin = pin, at = any()) } returns pin.copy(softDeletedAt = null)

        // When
        val result = useCase.restore(pinId = pin.id, user = user)

        // Then
        verify { pinRepository.restorePin(pin = pin, at = any()) }
        assert(result.softDeletedAt == null)
        verify(exactly = 0) { mediaRepository.deleteByPinId(any()) }
        verify(exactly = 0) { mediaStore.delete(any()) }
    }

    @Test
    fun `Given an owned recycled pin, Then restore hands the repository the clock's instant`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        val stampedInstant = slot<Instant>()
        every { pinRepository.findPinById(pin.id) } returns pin
        every { pinRepository.restorePin(pin = pin, at = any()) } returns pin.copy(softDeletedAt = null)

        // When
        useCase.restore(pinId = pin.id, user = user)

        // Then
        verify { pinRepository.restorePin(pin = pin, at = capture(stampedInstant)) }
        assertEquals(transitionInstant, stampedInstant.captured)
    }

    @Test
    fun `Given active pin, Then restore throws PinDeletionPinNotSoftDeletedError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user)
        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinDeletionPinNotSoftDeletedError> {
            useCase.restore(pinId = pin.id, user = user)
        }
    }

    // --- In bulk ---

    @Test
    fun `Given owned active pins, Then softDeleteAll recycles them at the clock's instant`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pinIds = givenPins(createPin(author = user), createPin(author = user))
        justRun { pinRepository.softDeletePins(any(), any()) }

        // When
        useCase.softDeleteAll(pinIds = pinIds, user = user)

        // Then
        verify(exactly = 1) { pinRepository.softDeletePins(pinIds, transitionInstant) }
    }

    @Test
    fun `Given a recycled pin after an active one, Then softDeleteAll refuses it before writing`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pinIds = givenPins(createPin(author = user), createPin(author = user, softDeletedAt = TestTime.now))

        // When, Then
        assertThrows<PinDeletionPinAlreadySoftDeletedError> { useCase.softDeleteAll(pinIds = pinIds, user = user) }
        verify(exactly = 0) { pinRepository.softDeletePins(any(), any()) }
    }

    @Test
    fun `Given owned recycled pins, Then restoreAll restores them at the clock's instant`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pinIds = givenPins(
            createPin(author = user, softDeletedAt = TestTime.now),
            createPin(author = user, softDeletedAt = TestTime.now),
        )
        justRun { pinRepository.restorePins(any(), any()) }

        // When
        useCase.restoreAll(pinIds = pinIds, user = user)

        // Then
        verify(exactly = 1) { pinRepository.restorePins(pinIds, transitionInstant) }
    }

    @Test
    fun `Given an unknown pin after a recycled one, Then restoreAll refuses it before writing`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val recycled = createPin(author = user, softDeletedAt = TestTime.now)
        val pinIds = listOf(recycled.id, randomUUID())
        every { pinRepository.findPinsByIds(pinIds) } returns listOf(recycled)

        // When, Then
        assertThrows<PinDeletionPinDoesNotExistError> { useCase.restoreAll(pinIds = pinIds, user = user) }
        verify(exactly = 0) { pinRepository.restorePins(any(), any()) }
    }

    @Test
    fun `Given another user's pin before an unknown one, Then softDeleteAll refuses the first as the batch orders`() {
        // Given: ADR 0039 answers the error the first refused identifier earns, not the unknown ids first
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val theirs = createPin(author = User(id = randomUUID(), name = "Jane Roe", createdAt = TestTime.now))
        val pinIds = listOf(theirs.id, randomUUID())
        every { pinRepository.findPinsByIds(pinIds) } returns listOf(theirs)

        // When, Then
        assertThrows<PinDeletionPermissionError> { useCase.softDeleteAll(pinIds = pinIds, user = user) }
    }

    /** The pins the repository answers in one read; returns their ids, in order. */
    private fun givenPins(vararg pins: Pin): List<UUID> {
        val ids = pins.map { it.id }
        every { pinRepository.findPinsByIds(ids) } returns pins.toList()
        return ids
    }

    // --- Permanent delete ---

    @Test
    fun `Given soft-deleted pin with an image, Then permanent delete removes the image row and file`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        val media = createMedia(pin.id)
        every { pinRepository.findPinById(pin.id) } returns pin
        every { mediaRepository.findByPinId(pin.id) } returns media
        justRun { pinRepository.permanentlyDeletePin(pin) }

        // When
        useCase.permanentlyDelete(pinId = pin.id, user = user)

        // Then
        verifyOrder {
            mediaRepository.deleteByPinId(pin.id)
            pinRepository.permanentlyDeletePin(pin)
            mediaStore.delete(media.storageKey)
        }
        verify { renditionCache.evictMedia(media.id) }
    }

    @Test
    fun `Given soft-deleted pin without an image, Then permanent delete succeeds without touching the image store`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        every { pinRepository.findPinById(pin.id) } returns pin
        every { mediaRepository.findByPinId(pin.id) } returns null
        justRun { pinRepository.permanentlyDeletePin(pin) }

        // When
        useCase.permanentlyDelete(pinId = pin.id, user = user)

        // Then
        verify { mediaRepository.deleteByPinId(pin.id) }
        verify { pinRepository.permanentlyDeletePin(pin) }
        verify(exactly = 0) { mediaStore.delete(any()) }
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test
    fun `Given active pin, Then permanent delete throws PinDeletionPinNotSoftDeletedError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user)
        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinDeletionPinNotSoftDeletedError> {
            useCase.permanentlyDelete(pinId = pin.id, user = user)
        }
    }

    @Test
    fun `Given a permanently deleted pin, Then its download is cleared`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        val pinId = pin.id
        every { pinRepository.findPinById(pinId) } returns pin
        every { mediaRepository.findByPinId(pinId) } returns null
        justRun { pinRepository.permanentlyDeletePin(pin) }

        // When
        useCase.permanentlyDelete(pinId = pinId, user = user)

        // Then
        verify { clearPinDownload.clear(pinId) }
    }

    // --- Empty recycle bin ---

    @Test
    fun `Given user with no soft-deleted pins, Then empty recycle bin does not touch any image`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        every { pinRepository.findAllSoftDeletedPinsForUser(user) } returns emptyList()
        justRun { pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user) }

        // When
        useCase.emptyRecycleBin(user = user)

        // Then
        verify { pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user) }
        verify(exactly = 0) { mediaRepository.deleteByPinId(any()) }
        verify(exactly = 0) { mediaStore.delete(any()) }
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test
    fun `Given soft-deleted pins some with images, Then empty recycle bin deletes rows and files for those`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pinWithMedia = createPin(author = user, softDeletedAt = TestTime.now)
        val pinWithoutMedia = createPin(author = user, softDeletedAt = TestTime.now)
        val media = createMedia(pinWithMedia.id)
        every { pinRepository.findAllSoftDeletedPinsForUser(user) } returns listOf(pinWithMedia, pinWithoutMedia)
        every { mediaRepository.findByPinId(pinWithMedia.id) } returns media
        every { mediaRepository.findByPinId(pinWithoutMedia.id) } returns null
        justRun { pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user) }

        // When
        useCase.emptyRecycleBin(user = user)

        // Then
        // Lock the cascade ordering: the enumerate + media-row deletes MUST happen before the
        // bulk pin delete (else the collect-before-bulk-delete step would silently leak every
        // file), and the file delete MUST happen after the bulk pin delete.
        verifyOrder {
            pinRepository.findAllSoftDeletedPinsForUser(user)
            mediaRepository.deleteByPinId(pinWithMedia.id)
            mediaRepository.deleteByPinId(pinWithoutMedia.id)
            pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user)
            mediaStore.delete(media.storageKey)
        }
        verify(exactly = 1) { mediaStore.delete(any()) }
        verify(exactly = 1) { renditionCache.evictMedia(any()) }
        verify { renditionCache.evictMedia(media.id) }
    }

    @Test
    fun `Given soft-deleted pins, Then empty recycle bin clears each pin's download`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val firstPin = createPin(author = user, softDeletedAt = TestTime.now)
        val secondPin = createPin(author = user, softDeletedAt = TestTime.now)
        every { pinRepository.findAllSoftDeletedPinsForUser(user) } returns listOf(firstPin, secondPin)
        every { mediaRepository.findByPinId(any()) } returns null
        justRun { pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user) }

        // When
        useCase.emptyRecycleBin(user = user)

        // Then
        verify { clearPinDownload.clear(firstPin.id) }
        verify { clearPinDownload.clear(secondPin.id) }
    }

    // --- Best-effort cleanup ---

    @Test
    fun `Given the image store throws on permanent delete, Then the delete still succeeds`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        val media = createMedia(pin.id)
        every { pinRepository.findPinById(pin.id) } returns pin
        every { mediaRepository.findByPinId(pin.id) } returns media
        justRun { pinRepository.permanentlyDeletePin(pin) }
        every { mediaStore.delete(any()) } throws RuntimeException("disk down")

        // When / Then: the row and pin are gone and no exception propagates
        assertDoesNotThrow { useCase.permanentlyDelete(pinId = pin.id, user = user) }
        verify { mediaRepository.deleteByPinId(pin.id) }
        verify { pinRepository.permanentlyDeletePin(pin) }
        verify { mediaStore.delete(media.storageKey) }
    }

    @Test
    fun `Given the image store throws on empty recycle bin, Then the bin still empties`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = createPin(author = user, softDeletedAt = TestTime.now)
        val media = createMedia(pin.id)
        every { pinRepository.findAllSoftDeletedPinsForUser(user) } returns listOf(pin)
        every { mediaRepository.findByPinId(pin.id) } returns media
        justRun { pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user) }
        every { mediaStore.delete(any()) } throws RuntimeException("disk down")

        // When / Then: the bulk delete ran and the file delete was still attempted
        assertDoesNotThrow { useCase.emptyRecycleBin(user = user) }
        verify { pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user) }
        verify { mediaStore.delete(media.storageKey) }
    }
}
