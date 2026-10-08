package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDirectionDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.BoardCreationInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.BoardInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinSortStrategyInputEnum
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.RemoteCollectionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardPinLister
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardRecycleBin
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardSummary
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardUpdater
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinBoardSetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.security.identity.SecurityIdentity
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BoardControllerTest {
    private val boardCreator = mockk<BoardCreator>()
    private val boardGetter = mockk<BoardGetter>()
    private val boardUpdater = mockk<BoardUpdater>()
    private val boardPinLister = mockk<BoardPinLister>()
    private val boardRecycleBin = mockk<BoardRecycleBin>()
    private val pinBoardSetter = mockk<PinBoardSetter>(relaxed = true)
    private val securityIdentity = mockk<SecurityIdentity>()
    // The real assembler over a stubbed resolver: the responses under assertion are the mapped ones.
    private val resolvePinMediaState =
        mockk<ResolvePinMediaState>().also {
            every { it.statesFor(any()) } returns emptyMap()
        }
    private val pinDuplicates = mockk<PinDuplicates>().also { every { it.pendingAmong(any()) } returns emptySet() }
    private val pinResponses = PinResponses(resolvePinMediaState, pinDuplicates)
    private val controller =
        BoardController(
            boardCreator = boardCreator,
            boardGetter = boardGetter,
            boardUpdater = boardUpdater,
            boardPinLister = boardPinLister,
            boardRecycleBin = boardRecycleBin,
            pinBoardSetter = pinBoardSetter,
            securityIdentity = securityIdentity,
            pinResponses = pinResponses,
        )

    private fun aUser() = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)

    private fun aBoard(author: User) =
        Board(
            id = randomUUID(),
            author = author,
            name = createRandomString(),
            description = createRandomString(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

    @Test
    fun `Given valid input, Then createBoard returns 201 with Location and a zero pin count`() {
        // Given
        val user = aUser()
        val dto = BoardCreationInputDto(name = createRandomString(), description = createRandomString())
        val board =
            Board(
                id = randomUUID(),
                author = user,
                name = dto.name,
                description = dto.description,
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { boardCreator.create(user, dto.name, dto.description, emptyList()) } returns board
        every { boardGetter.summarizeActiveBoardForUser(board.id, user) } returns BoardSummary(0, null, emptyList())

        // When
        val response = controller.createBoard(dto)

        // Then
        assertEquals(201, response.status)
        assertEquals("/api/v1/boards/${board.id}", response.getHeaderString("Location"))
        val body = response.entity as BoardOutputDto
        assertEquals(board.id, body.id)
        assertEquals(board.name, body.name)
        assertEquals(board.description, body.description)
        assertEquals(0, body.pinCount)
    }

    @Test
    fun `Given pinIds, Then createBoard hands them to the creator and answers the board's pin count`() {
        // Given
        val user = aUser()
        val board = aBoard(user)
        val pinIds = listOf(randomUUID(), randomUUID())
        val dto = BoardCreationInputDto(name = board.name, description = board.description, pinIds = pinIds)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { boardCreator.create(user, board.name, board.description, pinIds) } returns board
        every { boardGetter.summarizeActiveBoardForUser(board.id, user) } returns BoardSummary(2, null, emptyList())

        // When
        val response = controller.createBoard(dto)

        // Then
        assertEquals(2, (response.entity as BoardOutputDto).pinCount)
    }

    @Test
    fun `Given active boards for the user, Then listBoards returns each with its own pin count and cover`() {
        // Given
        val user = aUser()
        val boardA = aBoard(user)
        val boardB = aBoard(user)
        val coverPinId = randomUUID()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { boardGetter.listActiveBoardsForUser(user) } returns listOf(boardA, boardB)
        every { boardGetter.summarizeActiveBoardForUser(boardA.id, user) } returns
            BoardSummary(3, coverPinId, emptyList())
        every { boardGetter.summarizeActiveBoardForUser(boardB.id, user) } returns BoardSummary(0, null, emptyList())

        // When
        val response = controller.listBoards()

        // Then
        assertEquals(200, response.status)
        val body = response.entity as BoardListOutputDto
        assertEquals(
            listOf(
                BoardOutputDto(
                    id = boardA.id,
                    name = boardA.name,
                    description = boardA.description,
                    pinCount = 3,
                    coverUrl = "/api/v1/pins/$coverPinId/media",
                    remoteCollections = emptyList(),
                ),
                BoardOutputDto(
                    id = boardB.id,
                    name = boardB.name,
                    description = boardB.description,
                    pinCount = 0,
                    coverUrl = null,
                    remoteCollections = emptyList(),
                ),
            ),
            body.boards,
        )
    }

    @Test
    fun `Given an existing board, Then getBoard returns it with its pin count and collections`() {
        // Given
        val user = aUser()
        val board = aBoard(user)
        val collection =
            RemoteCollection(
                id = randomUUID(),
                author = user,
                url = "https://remote.test/${createRandomString()}",
                name = createRandomString(),
                board = board,
                createdAt = TestTime.now,
            )
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { boardGetter.getActiveBoardForUser(boardId = board.id, reader = user) } returns board
        every { boardGetter.summarizeActiveBoardForUser(board.id, user) } returns
            BoardSummary(5, null, listOf(collection))

        // When
        val response = controller.getBoard(board.id)

        // Then
        assertEquals(200, response.status)
        val body = response.entity as BoardOutputDto
        assertEquals(board.id, body.id)
        assertEquals(5, body.pinCount)
        assertEquals(
            listOf(RemoteCollectionOutputDto(name = collection.name, url = collection.url)),
            body.remoteCollections,
        )
    }

    @Test
    fun `Given valid input, Then updateBoard returns the updated board with its pin count`() {
        // Given
        val user = aUser()
        val boardId = randomUUID()
        val dto = BoardInputDto(name = createRandomString(), description = createRandomString())
        val updated =
            Board(
                id = boardId,
                author = user,
                name = dto.name,
                description = dto.description,
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            boardUpdater.update(boardId = boardId, name = dto.name, description = dto.description, user = user)
        } returns updated
        every { boardGetter.summarizeActiveBoardForUser(boardId, user) } returns BoardSummary(2, null, emptyList())

        // When
        val response = controller.updateBoard(boardId, dto)

        // Then
        assertEquals(200, response.status)
        val body = response.entity as BoardOutputDto
        assertEquals(dto.name, body.name)
        assertEquals(dto.description, body.description)
        assertEquals(2, body.pinCount)
    }

    @Test
    fun `Given an existing board, Then softDeleteBoard returns 204`() {
        // Given
        val user = aUser()
        val board = aBoard(user)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { boardRecycleBin.softDelete(boardId = board.id, user = user) } returns board

        // When
        val response = controller.softDeleteBoard(board.id)

        // Then
        assertEquals(204, response.status)
        verify { boardRecycleBin.softDelete(boardId = board.id, user = user) }
    }

    @Test
    fun `Given no cursor, no page size and no sort, Then listBoardPins uses defaults`() {
        // Given
        val user = aUser()
        val boardId = randomUUID()
        val page = Page<Pin>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            boardPinLister.listActivePinsForBoard(
                reader = user,
                boardId = boardId,
                cursor = null,
                pageSize = BoardController.DEFAULT_PAGE_SIZE,
                sort = PinSortStrategy.CREATED_AT_ASC,
            )
        } returns page

        // When
        val response =
            controller.listBoardPins(
                boardId = boardId,
                cursorInput = null,
                pageSizeInput = null,
                sortInput = null,
            )

        // Then
        assertEquals(200, response.status)
        val body = response.entity as PinListOutputDto
        assertEquals(emptyList<Any>(), body.pins)
    }

    @Test
    fun `Given cursor, page size and sort provided, Then listBoardPins uses the provided values`() {
        // Given
        val user = aUser()
        val boardId = randomUUID()
        val pivotId = randomUUID()
        val cursorInput = CursorDto(pivotId = pivotId, direction = CursorDirectionDto.FORWARD)
        val pageSizeInput = 5
        val sortInput = PinSortStrategyInputEnum.CREATED_AT_DESC
        val page = Page<Pin>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            boardPinLister.listActivePinsForBoard(
                reader = user,
                boardId = boardId,
                cursor = match { it.pivotId == pivotId },
                pageSize = pageSizeInput,
                sort = PinSortStrategy.CREATED_AT_DESC,
            )
        } returns page

        // When
        val response =
            controller.listBoardPins(
                boardId = boardId,
                cursorInput = cursorInput,
                pageSizeInput = pageSizeInput,
                sortInput = sortInput,
            )

        // Then
        assertEquals(200, response.status)
    }
}
