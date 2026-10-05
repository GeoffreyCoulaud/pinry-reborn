package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class PinDuplicatesTest {
    private val duplicateRepository = mockk<PinDuplicateRepositoryInterface>()
    private val useCase = PinDuplicates(duplicateRepository)

    private val user = User(randomUUID(), createRandomString(), createdAt = TestTime.now)

    private fun pin() = Pin(randomUUID(), user, null, null, "", emptyList(), emptyList(), TestTime.now, TestTime.now)

    @Test
    fun `Given a page of pins, Then the pending ones are asked of the repository in one call`() {
        // Given
        val pins = List(3) { pin() }
        val pending = setOf(pins.first().id)
        every { duplicateRepository.findPinIdsWithPending(pins.map { it.id }) } returns pending

        // When
        val found = useCase.pendingAmong(pins)

        // Then
        assertEquals(pending, found)
        verify(exactly = 1) { duplicateRepository.findPinIdsWithPending(any()) }
    }
}
