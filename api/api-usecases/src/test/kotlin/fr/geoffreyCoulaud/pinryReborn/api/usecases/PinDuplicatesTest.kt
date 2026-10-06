package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinRetrievalPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PinDuplicatesTest {
    private val duplicateRepository = mockk<PinDuplicateRepositoryInterface>()
    private val pinRepository = mockk<PinRepositoryInterface>()
    private val useCase = PinDuplicates(duplicateRepository, pinRepository, PinGetter(pinRepository))

    private val user = User(randomUUID(), createRandomString(), createdAt = TestTime.now)

    private fun storedPin(author: User = user, createdAt: Instant = TestTime.now): Pin {
        val pin = Pin(randomUUID(), author, null, null, "", emptyList(), emptyList(), createdAt, createdAt)
        every { pinRepository.findPinById(pin.id) } returns pin
        return pin
    }

    @Test
    fun `Given a pin's shown pairs, Then its duplicates are listed oldest pin first with their rejection`() {
        // Given
        val pin = storedPin()
        val newer = storedPin(createdAt = TestTime.now.plusSeconds(1))
        val older = storedPin(createdAt = TestTime.now.minusSeconds(1))
        every { duplicateRepository.findShownFor(pin.id) } returns mapOf(newer.id to false, older.id to true)
        every { pinRepository.findPinsByIds(any()) } returns listOf(newer, older)

        // When
        val duplicates = useCase.list(pin.id, user)

        // Then
        assertEquals(listOf(PinDuplicate(older, rejected = true), PinDuplicate(newer, rejected = false)), duplicates)
    }

    @Test
    fun `Given another user's pin, Then its duplicates are refused before any pair is read`() {
        // Given
        val pin = storedPin(author = User(randomUUID(), createRandomString(), createdAt = TestTime.now))

        // When, Then
        assertThrows<PinRetrievalPermissionError> { useCase.list(pin.id, user) }
        verify(exactly = 0) { duplicateRepository.findShownFor(any()) }
    }

    @Test
    fun `Given a page of pins, Then the pending ones are asked of the repository in one call`() {
        // Given
        val pins = List(3) { storedPin() }
        val pending = setOf<UUID>(pins.first().id)
        every { duplicateRepository.findPinIdsWithPending(pins.map { it.id }) } returns pending

        // When
        val found = useCase.pendingAmong(pins)

        // Then
        assertEquals(pending, found)
        verify(exactly = 1) { duplicateRepository.findPinIdsWithPending(any()) }
    }
}
