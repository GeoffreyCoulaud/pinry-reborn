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
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
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
        every { boardRepository.saveBoard(any()) } throws BoardNameAlreadyTakenException(cause = Exception("boom"))
        every { boardRepository.findBoardForUserByName(user = author, name = name) } returns holder

        // When
        val error = assertThrows<BoardNameAlreadyExistsError> {
            useCase.create(author = author, name = name, description = createRandomString())
        }

        // Then
        assertEquals(ErrorCode.BOARD_NAME_ALREADY_EXISTS, error.code)
        assertFalse(error.message.orEmpty().contains(RECYCLE_BIN_WORDING))
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
    fun `Given owned pins, Then create files each under the new board`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val other = board(author = author, name = createRandomString())
        val first = pin(author).copy(boards = listOf(other))
        val second = pin(author)
        givenPins(first, second)
        every { boardRepository.saveBoard(any()) } answers { firstArg() }
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        val board = useCase.create(author, createRandomString(), createRandomString(), listOf(first.id, second.id))

        // Then
        val saved = mutableListOf<Pin>()
        verify { pinRepository.savePin(capture(saved)) }
        assertEquals(listOf(listOf(other, board), listOf(board)), saved.map { it.boards })
        assertEquals(listOf(clockInstant, clockInstant), saved.map { it.updatedAt })
    }

    @Test
    fun `Given a pin named twice, Then create files it once`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val subject = pin(author)
        givenPins(subject)
        every { boardRepository.saveBoard(any()) } answers { firstArg() }
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        useCase.create(author, createRandomString(), createRandomString(), listOf(subject.id, subject.id))

        // Then
        verify(exactly = 1) { pinRepository.savePin(any()) }
    }

    @Test
    fun `Given pins, Then the board and every pin are saved inside one transaction`() {
        // Given
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val first = pin(author)
        val second = pin(author)
        givenPins(first, second)
        val seen = mutableListOf<Int?>()
        every { boardRepository.saveBoard(any()) } answers { seen += transactions.current; firstArg() }
        every { pinRepository.savePin(any()) } answers { seen += transactions.current; firstArg() }

        // When
        useCase.create(author, createRandomString(), createRandomString(), listOf(first.id, second.id))

        // Then: three writes, one transaction, none outside it
        assertEquals(3, seen.size)
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
        every { pinRepository.findPinById(unknownPinId) } returns null

        // When, Then
        assertThrows<PinBoardSettingPinDoesNotExistError> {
            useCase.create(author, createRandomString(), createRandomString(), listOf(unknownPinId))
        }
        verify(exactly = 0) { boardRepository.saveBoard(any()) }
    }

    private inline fun <reified T : Throwable> assertRefusedBeforeWriting(author: User, refused: Pin) {
        // Given: an acceptable pin first, so the refusal is not merely the first read
        val own = pin(author)
        givenPins(own, refused)

        // When, Then
        assertThrows<T> {
            useCase.create(author, createRandomString(), createRandomString(), listOf(own.id, refused.id))
        }
        verify(exactly = 0) { boardRepository.saveBoard(any()) }
        verify(exactly = 0) { pinRepository.savePin(any()) }
    }

    private fun givenPins(vararg pins: Pin) = pins.forEach { every { pinRepository.findPinById(it.id) } returns it }

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
