package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalBoardDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingSoftDeletedPinError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.PassthroughTransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID.randomUUID

class PinBoardSetterTest {
    private val pinRepository = mockk<PinRepositoryInterface>()
    private val boardRepository = mockk<BoardRepositoryInterface>()
    private val clockInstant = Instant.parse("2026-07-23T10:00:00Z")
    private val clock = mockk<Clock> { every { now() } returns clockInstant }
    private val useCase =
        PinBoardSetter(
            pinRepository = pinRepository,
            boardRepository = boardRepository,
            clock = clock,
            transactionRunner = PassthroughTransactionRunner(),
        )

    // --- The resolution half [PinUpdater] calls ---

    @Test
    fun `Given owned boards, Then resolveBoards answers them in order`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val first = board(user, "First")
        val second = board(user, "Second")
        every { boardRepository.findActiveBoardById(first.id) } returns first
        every { boardRepository.findActiveBoardById(second.id) } returns second

        // When
        val result = useCase.resolveBoards(boardIds = listOf(first.id, second.id), user = user)

        // Then
        assertEquals(listOf(first, second), result)
    }

    @Test
    fun `Given an unknown boardId, Then resolveBoards throws BoardRetrievalBoardDoesNotExistError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val unknownBoardId = randomUUID()
        every { boardRepository.findActiveBoardById(unknownBoardId) } returns null

        // When, Then
        assertThrows<BoardRetrievalBoardDoesNotExistError> {
            useCase.resolveBoards(boardIds = listOf(unknownBoardId), user = user)
        }
    }

    @Test
    fun `Given a board owned by another user, Then resolveBoards throws BoardRetrievalPermissionError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val stranger = User(id = randomUUID(), name = "Jane Roe", createdAt = TestTime.now)
        val theirs = board(stranger, "Not yours")
        every { boardRepository.findActiveBoardById(theirs.id) } returns theirs

        // When, Then
        assertThrows<BoardRetrievalPermissionError> {
            useCase.resolveBoards(boardIds = listOf(theirs.id), user = user)
        }
    }

    // --- Bulk membership ---

    @Test
    fun `Given a pin already in another board, Then addPinsToBoard keeps that one and adds the target`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val other = board(user, "Other")
        val target = board(user, "Target")
        val subject = pin(user).copy(boards = listOf(other))
        every { boardRepository.findActiveBoardById(target.id) } returns target
        every { pinRepository.findPinById(subject.id) } returns subject
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        useCase.addPinsToBoard(boardId = target.id, pinIds = listOf(subject.id), user = user)

        // Then
        val saved = slot<Pin>()
        verify { pinRepository.savePin(capture(saved)) }
        assertEquals(listOf(other, target), saved.captured.boards)
        assertEquals(clockInstant, saved.captured.updatedAt)
    }

    @Test
    fun `Given a pin in the target board, Then removePinsFromBoard drops it and keeps the other`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val other = board(user, "Other")
        val target = board(user, "Target")
        val subject = pin(user).copy(boards = listOf(other, target))
        every { boardRepository.findActiveBoardById(target.id) } returns target
        every { pinRepository.findPinById(subject.id) } returns subject
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        useCase.removePinsFromBoard(boardId = target.id, pinIds = listOf(subject.id), user = user)

        // Then
        val saved = slot<Pin>()
        verify { pinRepository.savePin(capture(saved)) }
        assertEquals(listOf(other), saved.captured.boards)
    }

    @Test
    fun `Given another user's pin last, Then addPinsToBoard refuses before writing the first`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val stranger = User(id = randomUUID(), name = "Jane Roe", createdAt = TestTime.now)
        val target = board(user, "Target")
        val own = pin(user)
        val theirs = pin(stranger)
        every { boardRepository.findActiveBoardById(target.id) } returns target
        every { pinRepository.findPinById(own.id) } returns own
        every { pinRepository.findPinById(theirs.id) } returns theirs

        // When, Then
        assertThrows<PinBoardSettingPermissionError> {
            useCase.addPinsToBoard(boardId = target.id, pinIds = listOf(own.id, theirs.id), user = user)
        }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given a missing pin, Then addPinsToBoard throws PinBoardSettingPinDoesNotExistError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val target = board(user, "Target")
        val missingPinId = randomUUID()
        every { boardRepository.findActiveBoardById(target.id) } returns target
        every { pinRepository.findPinById(missingPinId) } returns null

        // When, Then
        assertThrows<PinBoardSettingPinDoesNotExistError> {
            useCase.addPinsToBoard(boardId = target.id, pinIds = listOf(missingPinId), user = user)
        }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given a recycled pin, Then addPinsToBoard throws PinBoardSettingSoftDeletedPinError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val target = board(user, "Target")
        val recycled = pin(user).copy(softDeletedAt = TestTime.now)
        every { boardRepository.findActiveBoardById(target.id) } returns target
        every { pinRepository.findPinById(recycled.id) } returns recycled

        // When, Then
        assertThrows<PinBoardSettingSoftDeletedPinError> {
            useCase.addPinsToBoard(boardId = target.id, pinIds = listOf(recycled.id), user = user)
        }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given an unknown board, Then removePinsFromBoard throws BoardRetrievalBoardDoesNotExistError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val unknownBoardId = randomUUID()
        every { boardRepository.findActiveBoardById(unknownBoardId) } returns null

        // When, Then
        assertThrows<BoardRetrievalBoardDoesNotExistError> {
            useCase.removePinsFromBoard(boardId = unknownBoardId, pinIds = listOf(randomUUID()), user = user)
        }
    }

    private fun board(author: User, name: String) =
        Board(id = randomUUID(), author = author, name = name, description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)

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
