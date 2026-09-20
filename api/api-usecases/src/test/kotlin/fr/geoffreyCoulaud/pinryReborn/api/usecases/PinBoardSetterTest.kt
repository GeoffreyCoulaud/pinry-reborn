package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
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

    @Test
    fun `Given an owned active pin and valid owned boards, Then setBoards replaces the pin's boards`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val oldBoard = Board(id = randomUUID(), author = user, name = "Old", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val pin = Pin(
            id = randomUUID(),
            author = user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = listOf(oldBoard),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )
        val newBoard1 = Board(id = randomUUID(), author = user, name = "New 1", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val newBoard2 = Board(id = randomUUID(), author = user, name = "New 2", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)

        every { pinRepository.findPinById(pin.id) } returns pin
        every { boardRepository.findActiveBoardById(newBoard1.id) } returns newBoard1
        every { boardRepository.findActiveBoardById(newBoard2.id) } returns newBoard2
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        val result = useCase.setBoards(
            pinId = pin.id,
            boardIds = listOf(newBoard1.id, newBoard2.id),
            user = user,
        )

        // Then
        assertEquals(listOf(newBoard1, newBoard2), result.boards)
    }

    @Test
    fun `Given a missing pin, Then throws PinBoardSettingPinDoesNotExistError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val nonExistentPinId = randomUUID()

        every { pinRepository.findPinById(nonExistentPinId) } returns null

        // When, Then
        assertThrows<PinBoardSettingPinDoesNotExistError> {
            useCase.setBoards(pinId = nonExistentPinId, boardIds = emptyList(), user = user)
        }
    }

    @Test
    fun `Given a pin owned by another user, Then throws PinBoardSettingPermissionError`() {
        // Given
        val owner = User(id = randomUUID(), name = "Owner", createdAt = TestTime.now)
        val otherUser = User(id = randomUUID(), name = "Other", createdAt = TestTime.now)
        val pin = Pin(
            id = randomUUID(),
            author = owner,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = emptyList(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinBoardSettingPermissionError> {
            useCase.setBoards(pinId = pin.id, boardIds = emptyList(), user = otherUser)
        }
    }

    @Test
    fun `Given a soft-deleted pin, Then throws PinBoardSettingSoftDeletedPinError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = Pin(
            id = randomUUID(),
            author = user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = emptyList(),
            softDeletedAt = TestTime.now,
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

        every { pinRepository.findPinById(pin.id) } returns pin

        // When, Then
        assertThrows<PinBoardSettingSoftDeletedPinError> {
            useCase.setBoards(pinId = pin.id, boardIds = emptyList(), user = user)
        }
    }

    @Test
    fun `Given an unresolved boardId, Then throws BoardRetrievalBoardDoesNotExistError and saves nothing`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = Pin(
            id = randomUUID(),
            author = user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = emptyList(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )
        val badBoardId = randomUUID()

        every { pinRepository.findPinById(pin.id) } returns pin
        every { boardRepository.findActiveBoardById(badBoardId) } returns null

        // When, Then
        assertThrows<BoardRetrievalBoardDoesNotExistError> {
            useCase.setBoards(pinId = pin.id, boardIds = listOf(badBoardId), user = user)
        }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given a board owned by another user, Then throws BoardRetrievalPermissionError`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val otherUser = User(id = randomUUID(), name = "Other", createdAt = TestTime.now)
        val pin = Pin(
            id = randomUUID(),
            author = user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = emptyList(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )
        val othersBoard = Board(id = randomUUID(), author = otherUser, name = "Not yours", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)

        every { pinRepository.findPinById(pin.id) } returns pin
        every { boardRepository.findActiveBoardById(othersBoard.id) } returns othersBoard

        // When, Then
        assertThrows<BoardRetrievalPermissionError> {
            useCase.setBoards(pinId = pin.id, boardIds = listOf(othersBoard.id), user = user)
        }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given an empty boardIds list, Then the pin's boards are cleared`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val existingBoard = Board(id = randomUUID(), author = user, name = "Old", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val pin = Pin(
            id = randomUUID(),
            author = user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = emptyList(),
            boards = listOf(existingBoard),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

        every { pinRepository.findPinById(pin.id) } returns pin
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        val result = useCase.setBoards(pinId = pin.id, boardIds = emptyList(), user = user)

        // Then
        assertEquals(emptyList<Board>(), result.boards)
    }

    @Test
    fun `Given a pin recycled between the read and the fence, Then setBoards refuses and saves nothing`() {
        // Given: the first read answers an active pin, the fence's re-read a recycled one
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val board = Board(id = randomUUID(), author = user, name = "B", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val pin = pin(user)
        every { pinRepository.findPinById(pin.id) } returnsMany listOf(pin, pin.copy(softDeletedAt = TestTime.now))
        every { boardRepository.findActiveBoardById(board.id) } returns board
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When, Then
        assertThrows<PinBoardSettingSoftDeletedPinError> {
            useCase.setBoards(pinId = pin.id, boardIds = listOf(board.id), user = user)
        }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given a pin gone between the read and the fence, Then setBoards refuses it as absent`() {
        // Given: the fence's re-read finds no row
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val pin = pin(user)
        every { pinRepository.findPinById(pin.id) } returnsMany listOf(pin, null)

        // When, Then
        assertThrows<PinBoardSettingPinDoesNotExistError> {
            useCase.setBoards(pinId = pin.id, boardIds = emptyList(), user = user)
        }
    }

    @Test
    fun `Given tags set between the read and the fence, Then the saved pin carries them`() {
        // Given: the fence's re-read carries a tag the first read did not
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val board = Board(id = randomUUID(), author = user, name = "B", description = "",
            createdAt = TestTime.now, updatedAt = TestTime.now)
        val tag = Tag(id = randomUUID(), name = "tag", author = user, createdAt = TestTime.now)
        val pin = pin(user)
        every { pinRepository.findPinById(pin.id) } returnsMany listOf(pin, pin.copy(tags = listOf(tag)))
        every { boardRepository.findActiveBoardById(board.id) } returns board
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        val result = useCase.setBoards(pinId = pin.id, boardIds = listOf(board.id), user = user)

        // Then
        assertEquals(listOf(tag), result.tags)
        assertEquals(listOf(board), result.boards)
    }

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
