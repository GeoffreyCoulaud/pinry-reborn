package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DuplicateDecision
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DuplicateDecision.KEEP
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DuplicateDecision.MERGE
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DuplicateDecision.REJECT
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.DuplicateDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.DuplicateResolutionInvalidError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdateSoftDeletedPinError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.PassthroughTransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class DuplicateResolverTest {
    private val pinRepository = mockk<PinRepositoryInterface>()
    private val duplicateRepository = mockk<PinDuplicateRepositoryInterface>()
    private val clock = mockk<Clock> { every { now() } returns TestTime.now }
    private val useCase = DuplicateResolver(pinRepository, duplicateRepository, clock, PassthroughTransactionRunner())

    private val user = User(randomUUID(), "John Doe", createdAt = TestTime.now)

    private fun pin(description: String = "", createdAt: Instant = TestTime.now, author: User = user) =
        Pin(randomUUID(), author, null, null, description, emptyList(), emptyList(), createdAt, createdAt)

    // [open] lists every other pin as a shown duplicate.
    private fun stored(open: Pin, vararg others: Pin) {
        every { pinRepository.findPinsByIds(any()) } returns listOf(open) + others
        every { duplicateRepository.findShownFor(open.id) } returns others.associate { it.id to false }
        every { duplicateRepository.setRejected(any(), any(), any()) } just Runs
        every { pinRepository.savePin(any()) } answers { firstArg() }
        every { pinRepository.softDeletePins(any(), any()) } returns Unit
    }

    private fun verifyNothingWritten() {
        verify(exactly = 0) { duplicateRepository.setRejected(any(), any(), any()) }
        verify(exactly = 0) { pinRepository.savePin(any()) }
        verify(exactly = 0) { pinRepository.softDeletePins(any(), any()) }
    }

    @Test
    fun `Given not one kept pin, the open pin unnamed or rejected, or it alone, Then the decision is refused unread`() {
        // Given
        val (open, other, third) = List(3) { randomUUID() }
        val refused = listOf(
            mapOf(open to MERGE, other to MERGE),
            mapOf(open to KEEP, other to KEEP),
            mapOf(other to KEEP, third to MERGE),
            mapOf(open to REJECT, other to KEEP),
            mapOf(open to KEEP),
        )

        // When, Then
        refused.forEach { assertThrows<DuplicateResolutionInvalidError> { useCase.resolve(open, it, user) } }
        verify(exactly = 0) { pinRepository.findPinsByIds(any()) }
    }

    @Test
    fun `Given an unknown, foreign or recycled open pin, or a pin outside its list, Then nothing is written`() {
        // Given: the open pin lists [other] alone
        val (other, unlisted) = List(2) { pin() }
        val foreign = pin(author = User(randomUUID(), "Other", createdAt = TestTime.now))
        val recycled = pin().copy(softDeletedAt = TestTime.now)
        val resolve = { open: Pin, named: Map<UUID, DuplicateDecision> ->
            stored(open, other)
            useCase.resolve(open.id, mapOf(open.id to KEEP, other.id to MERGE) + named, user)
        }

        // When, Then
        stored(other)
        val unknown = randomUUID()
        assertThrows<PinUpdatePinDoesNotExistError> {
            useCase.resolve(unknown, mapOf(unknown to KEEP, other.id to MERGE), user)
        }
        assertThrows<PinUpdatePermissionError> { resolve(foreign, emptyMap()) }
        assertThrows<PinUpdateSoftDeletedPinError> { resolve(recycled, emptyMap()) }
        assertThrows<DuplicateDoesNotExistError> { resolve(pin(), mapOf(unlisted.id to REJECT)) }
        verifyNothingWritten()
    }

    @Test
    fun `Given the open pin merged into a candidate with a third, Then a fourth's pairs with all three are rejected`() {
        // Given
        val (open, kept, merged) = List(3) { pin() }
        val rejected = pin()
        stored(open, kept, merged, rejected)
        val decisions = mapOf(open.id to MERGE, kept.id to KEEP, merged.id to MERGE, rejected.id to REJECT)

        // When
        val answered = useCase.resolve(open.id, decisions, user)

        // Then
        assertEquals(kept.copy(updatedAt = TestTime.now), answered)
        listOf(open, kept, merged).forEach {
            verify { duplicateRepository.setRejected(rejected.id, it.id, TestTime.now) }
        }
        verify { pinRepository.softDeletePins(match { it.toSet() == setOf(open.id, merged.id) }, TestTime.now) }
    }

    @Test
    fun `Given two absorbed pins with descriptions, the newer named first, Then a blank kept one takes the older's`() {
        // Given
        val open = pin()
        val newer = pin("Newer", createdAt = TestTime.now.plusSeconds(1)).copy(sourceContextUrl = "https://a.test/n")
        val older = pin("Older", createdAt = TestTime.now.minusSeconds(1)).copy(sourceContextUrl = "https://a.test/o")
        stored(open, newer, older)
        val decisions = linkedMapOf(open.id to KEEP, newer.id to MERGE, older.id to MERGE)

        // When
        val answered = useCase.resolve(open.id, decisions, user)

        // Then
        assertEquals("Older" to "https://a.test/o", answered.description to answered.sourceContextUrl)
    }

    @Test
    fun `Given a kept pin with a description and a page, Then it keeps both and gains the absorbed tags and boards`() {
        // Given
        val tag = Tag(randomUUID(), user, "absorbed", TestTime.now)
        val board = Board(randomUUID(), user, "Absorbed", "", TestTime.now, TestTime.now)
        val open = pin("Kept").copy(sourceContextUrl = "https://a.test/kept")
        val absorbed = pin("Absorbed").copy(sourceContextUrl = "https://a.test/absorbed", tags = listOf(tag),
            boards = listOf(board))
        stored(open, absorbed)

        // When
        val answered = useCase.resolve(open.id, mapOf(open.id to KEEP, absorbed.id to MERGE), user)

        // Then
        assertEquals(open.copy(tags = listOf(tag), boards = listOf(board), updatedAt = TestTime.now), answered)
    }

    @Test
    fun `Given rejections alone, Then the open pin is answered as it was and no pin is saved or recycled`() {
        // Given
        val (open, rejected, unnamed) = List(3) { pin() }
        stored(open, rejected, unnamed)

        // When
        val answered = useCase.resolve(open.id, mapOf(open.id to KEEP, rejected.id to REJECT), user)

        // Then
        assertEquals(open, answered)
        verify(exactly = 1) { duplicateRepository.setRejected(any(), any(), any()) }
        verify { duplicateRepository.setRejected(rejected.id, open.id, TestTime.now) }
        verify(exactly = 0) { pinRepository.savePin(any()) }
        verify(exactly = 0) { pinRepository.softDeletePins(any(), any()) }
    }
}
