package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdateSoftDeletedPinError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.PassthroughTransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID.randomUUID

class PinMergerTest {
    private val pinRepository = mockk<PinRepositoryInterface>()
    private val clock = mockk<Clock> { every { now() } returns TestTime.now }
    private val transactionRunner = PassthroughTransactionRunner()
    private val duplicateResolver = DuplicateResolver(pinRepository, mockk(), clock, transactionRunner)
    private val useCase = PinMerger(pinRepository, duplicateResolver, clock, transactionRunner)

    private val earlier = TestTime.now.minusSeconds(60)
    private val user = User(randomUUID(), "John Doe", createdAt = TestTime.now)

    private fun tag(name: String) = Tag(randomUUID(), user, name, TestTime.now)

    private fun board(name: String) = Board(randomUUID(), user, name, "", TestTime.now, TestTime.now)

    private fun pin(
        description: String = "",
        sourceContextUrl: String? = null,
        tags: List<Tag> = emptyList(),
        boards: List<Board> = emptyList(),
        author: User = user,
    ) = Pin(
        randomUUID(), author, sourceContextUrl, "https://example.com/${randomUUID()}.jpg", description, tags, boards,
        earlier, earlier,
    )

    private fun stored(vararg pins: Pin) {
        every { pinRepository.findPinsByIds(any()) } returns pins.toList()
        every { pinRepository.savePin(any()) } answers { firstArg() }
        every { pinRepository.softDeletePins(any(), any()) } returns Unit
    }

    @Test
    fun `Given a blank kept pin, Then it gains the union of tags and boards and the first description and page`() {
        // Given
        val (shared, own, absorbedOnly) = listOf(tag("shared"), tag("own"), tag("absorbed"))
        val kept = pin(tags = listOf(shared, own), boards = listOf(board("kept")))
        val blank = pin(description = " ", tags = listOf(shared))
        val filled = pin("Filled", sourceContextUrl = "https://example.com/page", tags = listOf(absorbedOnly))
        val later = pin(description = "Later", sourceContextUrl = "https://example.com/later")
        stored(kept, blank, filled, later)

        // When
        val merged = useCase.merge(kept.id, listOf(blank.id, filled.id, later.id), user)

        // Then
        val expected = kept.copy(
            description = "Filled",
            sourceContextUrl = "https://example.com/page",
            tags = listOf(shared, own, absorbedOnly),
            updatedAt = TestTime.now,
        )
        assertEquals(expected, merged)
        verify { pinRepository.softDeletePins(listOf(blank.id, filled.id, later.id), TestTime.now) }
    }

    @Test
    fun `Given a kept pin with a description and a page, Then it keeps both and gains the absorbed boards`() {
        // Given
        val kept = pin(description = "Kept", sourceContextUrl = "https://example.com/kept")
        val absorbed = pin(description = "Absorbed", sourceContextUrl = "https://example.com/absorbed",
            boards = listOf(board("absorbed")))
        stored(kept, absorbed)

        // When
        val merged = useCase.merge(kept.id, listOf(absorbed.id), user)

        // Then
        assertEquals(kept.copy(boards = absorbed.boards, updatedAt = TestTime.now), merged)
    }

    @Test
    fun `Given no absorbed pin with a description, Then the kept pin's blank one stays`() {
        // Given
        val (kept, absorbed) = List(2) { pin() }
        stored(kept, absorbed)

        // When, Then
        assertEquals("", useCase.merge(kept.id, listOf(absorbed.id), user).description)
    }

    @Test
    fun `Given a refused pin last in the list, Then the merge refuses it and writes nothing`() {
        // Given
        val (kept, absorbed) = List(2) { pin() }
        val foreign = pin(author = User(randomUUID(), "Other", createdAt = TestTime.now))
        val recycled = pin().copy(softDeletedAt = earlier)
        stored(kept, absorbed, foreign, recycled)
        val merge = { last: Pin -> useCase.merge(kept.id, listOf(absorbed.id, last.id), user) }

        // When, Then
        assertThrows<PinUpdatePinDoesNotExistError> { useCase.merge(kept.id, listOf(absorbed.id, randomUUID()), user) }
        assertThrows<PinUpdatePermissionError> { merge(foreign) }
        assertThrows<PinUpdateSoftDeletedPinError> { merge(recycled) }
        verify(exactly = 0) { pinRepository.savePin(any()) }
        verify(exactly = 0) { pinRepository.softDeletePins(any(), any()) }
    }
}
