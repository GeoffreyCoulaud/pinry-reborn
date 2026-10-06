package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class GetPinMediaTest : BaseTest() {
    private val pins = mockk<PinRepositoryInterface>()
    private val mediaRepository = mockk<MediaRepositoryInterface>()
    private val useCase = GetPinMedia(pins, mediaRepository)

    private val owner = User(randomUUID(), createRandomString(), createdAt = TestTime.now)
    private fun pin(author: User = owner) = Pin(randomUUID(), author, "https://c", null, "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)
    private fun mediaFor(pinId: UUID, hash: String = "h") = Media.StillImage(
        id = randomUUID(), pinId = pinId, mimeType = "image/png", width = 1, height = 1,
        byteSize = 1, contentHash = hash, storageKey = "originals/x/$pinId/i.png",
        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
    )

    @Test fun `Given the owner and an image, Then get returns it`() {
        val p = pin(); val img = mediaFor(p.id)
        every { pins.findPinById(p.id) } returns p
        every { mediaRepository.findByPinId(p.id) } returns img
        assertEquals(img, useCase.get(p.id, owner))
    }

    @Test fun `Given a missing pin, Then get throws MediaPinDoesNotExistError`() {
        every { pins.findPinById(any()) } returns null
        assertThrows(MediaPinDoesNotExistError::class.java) { useCase.get(randomUUID(), owner) }
    }

    @Test fun `Given a non-owner, Then get throws MediaPermissionError`() {
        val p = pin(author = User(randomUUID(), createRandomString(), createdAt = TestTime.now))
        every { pins.findPinById(p.id) } returns p
        assertThrows(MediaPermissionError::class.java) { useCase.get(p.id, owner) }
    }

    @Test fun `Given a pin without an image, Then get throws MediaDoesNotExistError`() {
        val p = pin()
        every { pins.findPinById(p.id) } returns p
        every { mediaRepository.findByPinId(p.id) } returns null
        assertThrows(MediaDoesNotExistError::class.java) { useCase.get(p.id, owner) }
    }
}
