package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalBoardDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BoardGetterTest {
    private val boardRepository: BoardRepositoryInterface = mockk()
    private val remoteCollectionRepository: RemoteCollectionRepositoryInterface = mockk()
    private val useCase =
        BoardGetter(boardRepository = boardRepository, remoteCollectionRepository = remoteCollectionRepository)

    @Test
    fun `Given an owned active board, Then getActiveBoardForUser returns it`() {
        // Given
        val reader = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val board =
            Board(
                id = randomUUID(),
                author = reader,
                name = createRandomString(),
                description = createRandomString(),
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        every { boardRepository.findActiveBoardById(board.id) } returns board

        // When
        val result = useCase.getActiveBoardForUser(boardId = board.id, reader = reader)

        // Then
        assertEquals(board, result)
    }

    @Test
    fun `Given a missing or recycled board, Then getActiveBoardForUser throws BoardRetrievalBoardDoesNotExistError`() {
        // Given
        val reader = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val boardId = randomUUID()
        every { boardRepository.findActiveBoardById(boardId) } returns null

        // When, Then
        assertThrows<BoardRetrievalBoardDoesNotExistError> {
            useCase.getActiveBoardForUser(boardId = boardId, reader = reader)
        }
    }

    @Test
    fun `Given a board owned by another user, Then getActiveBoardForUser throws BoardRetrievalPermissionError`() {
        // Given
        val reader = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val board =
            Board(
                id = randomUUID(),
                author = author,
                name = createRandomString(),
                description = createRandomString(),
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        every { boardRepository.findActiveBoardById(board.id) } returns board

        // When, Then
        assertThrows<BoardRetrievalPermissionError> {
            useCase.getActiveBoardForUser(boardId = board.id, reader = reader)
        }
    }

    @Test
    fun `Given a reader with boards, Then listActiveBoardsForUser delegates to the repository`() {
        // Given
        val reader = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val board =
            Board(
                id = randomUUID(),
                author = reader,
                name = createRandomString(),
                description = createRandomString(),
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        val expected = listOf(board)
        every { boardRepository.findActiveBoardsForUser(reader) } returns expected

        // When
        val result = useCase.listActiveBoardsForUser(reader = reader)

        // Then
        assertEquals(expected, result)
        verify { boardRepository.findActiveBoardsForUser(reader) }
    }

    @Test
    fun `Given an owned active board, Then summarizeActiveBoardForUser returns its count, cover and collections`() {
        // Given
        val reader = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val board =
            Board(
                id = randomUUID(),
                author = reader,
                name = createRandomString(),
                description = createRandomString(),
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        val coverPinId = randomUUID()
        every { boardRepository.findActiveBoardById(board.id) } returns board
        every { boardRepository.countActivePinsInBoard(board.id) } returns 42
        every { boardRepository.findCoverPinId(board.id) } returns coverPinId
        val collections =
            listOf(
                RemoteCollection(
                    id = randomUUID(),
                    author = reader,
                    url = checkNotNull(HttpUrl.parse("https://remote.test/${createRandomString()}")),
                    name = createRandomString(),
                    board = board,
                    createdAt = TestTime.now,
                )
            )
        every { remoteCollectionRepository.findRemoteCollectionsForBoard(board.id) } returns collections

        // When
        val summary = useCase.summarizeActiveBoardForUser(boardId = board.id, reader = reader)

        // Then
        assertEquals(BoardSummary(pinCount = 42, coverPinId = coverPinId, remoteCollections = collections), summary)
    }

    @Test
    fun `Given a board owned by another user, Then summarizeActiveBoardForUser throws BoardRetrievalPermissionError`() {
        // Given
        val reader = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val board =
            Board(
                id = randomUUID(),
                author = author,
                name = createRandomString(),
                description = createRandomString(),
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        every { boardRepository.findActiveBoardById(board.id) } returns board

        // When, Then
        assertThrows<BoardRetrievalPermissionError> {
            useCase.summarizeActiveBoardForUser(boardId = board.id, reader = reader)
        }
    }
}
