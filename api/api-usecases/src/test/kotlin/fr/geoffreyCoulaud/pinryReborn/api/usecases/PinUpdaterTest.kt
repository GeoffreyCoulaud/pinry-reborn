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
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class PinUpdaterTest {
    private val pinRepository = mockk<PinRepositoryInterface>()
    private val pinTagger = mockk<PinTagger>()
    private val pinBoardSetter = mockk<PinBoardSetter>()
    private val clockInstant = Instant.parse("2026-09-20T10:00:00Z")
    private val clock = mockk<Clock> { every { now() } returns clockInstant }
    private val useCase =
        PinUpdater(
            pinTagger = pinTagger,
            pinBoardSetter = pinBoardSetter,
            pinRepository = pinRepository,
            clock = clock,
            transactionRunner = PassthroughTransactionRunner(),
        )

    private val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
    private val tag = Tag(id = randomUUID(), name = "nature", author = user, createdAt = TestTime.now)
    private val board = Board(
        id = randomUUID(), author = user, name = "Board", description = "",
        createdAt = TestTime.now, updatedAt = TestTime.now,
    )

    @Test
    fun `Given an owned active pin, Then update replaces every field it was sent`() {
        // Given
        val pin = pin(author = user)
        every { pinRepository.findPinById(pin.id) } returns pin
        every { pinTagger.resolveTags(listOf("nature"), user) } returns listOf(tag)
        every { pinBoardSetter.resolveBoards(listOf(board.id), user) } returns listOf(board)
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        val result = update(pin.id, tagNames = listOf("nature"), boardIds = listOf(board.id))

        // Then
        assertEquals("A new description", result.description)
        assertEquals("https://example.com/new", result.sourceContextUrl)
        assertEquals(null, result.sourceMediaUrl)
        assertEquals(listOf(tag), result.tags)
        assertEquals(listOf(board), result.boards)
        assertEquals(clockInstant, result.updatedAt)
    }

    @Test
    fun `Given a missing pin, Then throws PinUpdatePinDoesNotExistError`() {
        // Given
        val missingPinId = randomUUID()
        every { pinRepository.findPinById(missingPinId) } returns null

        // When, Then
        assertThrows<PinUpdatePinDoesNotExistError> { update(missingPinId) }
    }

    @Test
    fun `Given a pin owned by another user, Then throws PinUpdatePermissionError`() {
        // Given
        val other = User(id = randomUUID(), name = "Other", createdAt = TestTime.now)
        val pin = pin(author = other)
        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinUpdatePermissionError> { update(pin.id) }
    }

    @Test
    fun `Given a soft-deleted pin, Then throws PinUpdateSoftDeletedPinError`() {
        // Given
        val pin = pin(author = user).copy(softDeletedAt = TestTime.now)
        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinUpdateSoftDeletedPinError> { update(pin.id) }
    }

    @Test
    fun `Given a pin recycled between the read and the fence, Then update refuses and saves nothing`() {
        // Given: the first read answers an active pin, the fence's re-read a recycled one
        val pin = pin(author = user)
        every { pinRepository.findPinById(pin.id) } returnsMany
            listOf(pin, pin.copy(softDeletedAt = TestTime.now))
        every { pinTagger.resolveTags(emptyList(), user) } returns emptyList()
        every { pinBoardSetter.resolveBoards(emptyList(), user) } returns emptyList()

        // When, Then
        assertThrows<PinUpdateSoftDeletedPinError> { update(pin.id) }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given a pin gone between the read and the fence, Then update refuses it as absent`() {
        // Given: the fence's re-read finds no row
        val pin = pin(author = user)
        every { pinRepository.findPinById(pin.id) } returnsMany listOf(pin, null)
        every { pinTagger.resolveTags(emptyList(), user) } returns emptyList()
        every { pinBoardSetter.resolveBoards(emptyList(), user) } returns emptyList()

        // When, Then
        assertThrows<PinUpdatePinDoesNotExistError> { update(pin.id) }
    }

    private fun update(
        pinId: UUID,
        tagNames: List<String> = emptyList(),
        boardIds: List<UUID> = emptyList(),
    ) = useCase.update(
        pinId = pinId,
        description = "A new description",
        sourceContextUrl = "https://example.com/new",
        sourceMediaUrl = null,
        tagNames = tagNames,
        boardIds = boardIds,
        user = user,
    )

    private fun pin(author: User) =
        Pin(
            id = randomUUID(),
            author = author,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = emptyList(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )
}
