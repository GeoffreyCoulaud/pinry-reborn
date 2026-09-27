package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.boards.BoardNameAlreadyTakenException
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardNameAlreadyExistsError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ErrorCode
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingSoftDeletedPinError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.PassthroughTransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID.randomUUID

class BoardCreatorTest {
    private val boardRepository: BoardRepositoryInterface = mockk()
    private val pinRepository: PinRepositoryInterface = mockk()
    private val clockInstant = Instant.parse("2026-07-23T10:00:00Z")
    private val clock = mockk<Clock> { every { now() } returns clockInstant }
    private val transactions = PassthroughTransactionRunner()
    private val useCase =
        BoardCreator(
            boardRepository = boardRepository,
            pinRepository = pinRepository,
            pinBoardSetter = PinBoardSetter(pinRepository, boardRepository, clock, transactions),
            clock = clock,
            transactionRunner = transactions,
        )

    init {
        // A board created without pins still goes through both bulk calls, which the repository answers empty.
        every { pinRepository.findPinsByIds(emptyList()) } returns emptyList()
        justRun { pinRepository.addPinsToBoard(emptyList(), any(), any()) }
    }

    @Test
    fun `Given valid input, Then create saves a new active board with the given fields`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val name = createRandomString()
        val description = createRandomString()
        every { boardRepository.saveBoard(any()) } answers { firstArg() }

        // When
        val board = useCase.create(author = author, name = name, description = description)

        // Then
        assertEquals(author, board.author)
        assertEquals(name, board.name)
        assertEquals(description, board.description)
        assertNull(board.softDeletedAt)
    }

    @Test
    fun `Given the name held by an active board, Then create rethrows BoardNameAlreadyExistsError`() {
        // Given: the index is the authority, so the refusal arrives from the store
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val name = createRandomString()
        val holder = board(author = author, name = name)
        val readIn = mutableListOf<Int?>()
        every { boardRepository.saveBoard(any()) } throws BoardNameAlreadyTakenException(cause = Exception("boom"))
        every { boardRepository.findBoardForUserByName(user = author, name = name) } answers {
            readIn += transactions.current
            holder
        }

        // When
        val error = assertThrows<BoardNameAlreadyExistsError> {
            useCase.create(author = author, name = name, description = createRandomString())
        }

        // Then: the holder is read after the rollback, outside the transaction
        assertEquals(ErrorCode.BOARD_NAME_ALREADY_EXISTS, error.code)
        assertFalse(error.message.orEmpty().contains(RECYCLE_BIN_WORDING))
        assertEquals(listOf(null), readIn)
    }

    @Test
    fun `Given the name held by a recycled board, Then the refusal says the recycle bin holds it`() {
        // Given: the index covers every row, so a client with an empty board list needs telling why
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val name = createRandomString()
        val holder = board(author = author, name = name).copy(softDeletedAt = TestTime.now)
        every { boardRepository.saveBoard(any()) } throws BoardNameAlreadyTakenException(cause = Exception("boom"))
        every { boardRepository.findBoardForUserByName(user = author, name = name) } returns holder

        // When
        val error = assertThrows<BoardNameAlreadyExistsError> {
            useCase.create(author = author, name = name, description = createRandomString())
        }

        // Then
        assertTrue(error.message.orEmpty().contains(RECYCLE_BIN_WORDING))
    }

    @Test
    fun `Given the holder hard-deleted before it is read back, Then create still rethrows the collision`() {
        // Given: a concurrent empty-the-bin between the violation and the lookup that explains it
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val name = createRandomString()
        every { boardRepository.saveBoard(any()) } throws BoardNameAlreadyTakenException(cause = Exception("boom"))
        every { boardRepository.findBoardForUserByName(user = author, name = name) } returns null

        // When
        val error = assertThrows<BoardNameAlreadyExistsError> {
            useCase.create(author = author, name = name, description = createRandomString())
        }

        // Then
        assertEquals(ErrorCode.BOARD_NAME_ALREADY_EXISTS, error.code)
        assertFalse(error.message.orEmpty().contains(RECYCLE_BIN_WORDING))
    }

    // --- Created with its pins ---

    @Test
    fun `Given owned pins, Then create files them under the new board`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val pinIds = givenPins(pin(author), pin(author))
        every { boardRepository.saveBoard(any()) } answers { firstArg() }
        justRun { pinRepository.addPinsToBoard(any(), any(), any()) }

        // When
        val board = useCase.create(author, createRandomString(), createRandomString(), pinIds)

        // Then
        verify(exactly = 1) { pinRepository.addPinsToBoard(pinIds, board, clockInstant) }
    }

    @Test
    fun `Given a pin named twice, Then create files it once`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val pinIds = givenPins(pin(author))
        every { boardRepository.saveBoard(any()) } answers { firstArg() }
        justRun { pinRepository.addPinsToBoard(any(), any(), any()) }

        // When
        useCase.create(author, createRandomString(), createRandomString(), pinIds + pinIds)

        // Then
        verify(exactly = 1) { pinRepository.addPinsToBoard(pinIds, any(), any()) }
    }

    @Test
    fun `Given pins, Then the board and every pin are saved inside one transaction`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val pinIds = givenPins(pin(author), pin(author))
        val seen = mutableListOf<Int?>()
        every { boardRepository.saveBoard(any()) } answers { seen += transactions.current; firstArg() }
        every { pinRepository.addPinsToBoard(any(), any(), any()) } answers { seen += transactions.current }

        // When
        useCase.create(author, createRandomString(), createRandomString(), pinIds)

        // Then: two writes, one transaction, none outside it
        assertEquals(2, seen.size)
        assertNotNull(seen.first())
        assertEquals(1, seen.toSet().size)
    }

    @Test
    fun `Given another account's pin, Then create refuses and saves no board`() {
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val stranger = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        assertRefusedBeforeWriting<PinBoardSettingPermissionError>(author, pin(stranger))
    }

    @Test
    fun `Given a recycled pin, Then create refuses and saves no board`() {
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        assertRefusedBeforeWriting<PinBoardSettingSoftDeletedPinError>(
            author,
            pin(author).copy(softDeletedAt = TestTime.now),
        )
    }

    @Test
    fun `Given an unknown pin, Then create refuses and saves no board`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val unknownPinId = randomUUID()
        every { pinRepository.findPinsByIds(listOf(unknownPinId)) } returns emptyList()

        // When, Then
        assertThrows<PinBoardSettingPinDoesNotExistError> {
            useCase.create(author, createRandomString(), createRandomString(), listOf(unknownPinId))
        }
        verify(exactly = 0) { boardRepository.saveBoard(any()) }
    }

    private inline fun <reified T : Throwable> assertRefusedBeforeWriting(author: User, refused: Pin) {
        // Given: an acceptable pin first, so the refusal is not merely the first read
        val pinIds = givenPins(pin(author), refused)

        // When, Then
        assertThrows<T> {
            useCase.create(author, createRandomString(), createRandomString(), pinIds)
        }
        verify(exactly = 0) { boardRepository.saveBoard(any()) }
        verify(exactly = 0) { pinRepository.addPinsToBoard(any(), any(), any()) }
    }

    /** The pins the repository answers in one read; returns their ids, in order. */
    private fun givenPins(vararg pins: Pin): List<UUID> {
        val ids = pins.map { it.id }
        every { pinRepository.findPinsByIds(ids) } returns pins.toList()
        return ids
    }

    private fun pin(author: User) =
        Pin(
            id = randomUUID(),
            author = author,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = createRandomString(),
            tags = emptyList(),
            boards = emptyList(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

    private fun board(author: User, name: String) =
        Board(
            id = randomUUID(),
            author = author,
            name = name,
            description = createRandomString(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

    private companion object {
        /** The wording the presentation layer hands the client; asserted, not the whole sentence. */
        const val RECYCLE_BIN_WORDING = "recycle bin"
    }
}
