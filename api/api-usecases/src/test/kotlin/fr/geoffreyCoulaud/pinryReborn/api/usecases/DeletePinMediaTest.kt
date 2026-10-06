package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class DeletePinMediaTest : BaseTest() {
    private val pins = mockk<PinRepositoryInterface>()
    private val mediaRepository = mockk<MediaRepositoryInterface>(relaxed = true)
    private val store = mockk<MediaStore>(relaxed = true)
    private val clearPinDownload = mockk<ClearPinDownload>(relaxed = true)
    private val renditionCache = mockk<RenditionCache>()
    private val duplicates = mockk<PinDuplicateRepositoryInterface>(relaxed = true)
    private val useCase = DeletePinMedia(pins, mediaRepository, duplicates, store, clearPinDownload, renditionCache)

    init { every { renditionCache.evictMedia(any()) } returns Unit }

    private val owner = User(randomUUID(), createRandomString(), createdAt = TestTime.now)
    private fun pin(author: User = owner) = Pin(randomUUID(), author, "https://c", null, "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)
    private fun mediaFor(pinId: UUID, hash: String = "h") = Media.StillImage(
        id = randomUUID(), pinId = pinId, mimeType = "image/png", width = 1, height = 1,
        byteSize = 1, contentHash = hash, storageKey = "originals/x/$pinId/i.png",
        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
    )

    @Test fun `Given the owner and an image, Then delete removes the row and the file`() {
        val p = pin(); val img = mediaFor(p.id)
        every { pins.findPinById(p.id) } returns p
        every { mediaRepository.findByPinId(p.id) } returns img

        useCase.delete(p.id, owner)

        verifyOrder {
            mediaRepository.deleteByPinId(p.id)
            duplicates.deletePending(p.id)
            store.delete(img.storageKey)
            clearPinDownload.clear(p.id)
        }
        verify { renditionCache.evictMedia(img.id) }
    }

    @Test fun `Given a missing pin, Then delete throws MediaPinDoesNotExistError`() {
        every { pins.findPinById(any()) } returns null
        assertThrows(MediaPinDoesNotExistError::class.java) { useCase.delete(randomUUID(), owner) }
    }

    @Test fun `Given a non-owner, Then delete throws MediaPermissionError`() {
        val p = pin(author = User(randomUUID(), createRandomString(), createdAt = TestTime.now))
        every { pins.findPinById(p.id) } returns p
        assertThrows(MediaPermissionError::class.java) { useCase.delete(p.id, owner) }
    }

    @Test fun `Given a pin without an image, Then delete throws MediaDoesNotExistError`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clearPinDownload.clear(p.id) } returns false
        assertThrows(MediaDoesNotExistError::class.java) { useCase.delete(p.id, owner) }
    }

    @Test fun `Given no image but a pending download, Then delete cancels it and does not throw`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { mediaRepository.findByPinId(p.id) } returns null
        every { clearPinDownload.clear(p.id) } returns true

        useCase.delete(p.id, owner)

        verify { clearPinDownload.clear(p.id) }
        verify(exactly = 0) { mediaRepository.deleteByPinId(any()) }
        verify(exactly = 0) { store.delete(any()) }
        verify(exactly = 0) { renditionCache.evictMedia(any()) }
    }

    @Test fun `Given the image store throws, Then the delete still succeeds`() {
        // Given
        val p = pin(); val img = mediaFor(p.id)
        every { pins.findPinById(p.id) } returns p
        every { mediaRepository.findByPinId(p.id) } returns img
        every { store.delete(any()) } throws RuntimeException("disk down")

        // When / Then: the row is removed and no exception propagates
        assertDoesNotThrow { useCase.delete(p.id, owner) }
        verify { mediaRepository.deleteByPinId(p.id) }
        verify { store.delete(img.storageKey) }
        verify { clearPinDownload.clear(p.id) }
    }
}
