package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.boardName
import fr.geoffreyCoulaud.pinryReborn.api.domain.boards.BoardNameAlreadyTakenException
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.BoardRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.EbeanMediaRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.UserRepository
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BoardRepositoryTest : RepositoryTest() {
    private val boardRepository = BoardRepository(persistor)
    private val pinRepository = PinRepository(persistor)
    private val userRepository = UserRepository(persistor)
    private val mediaRepository = EbeanMediaRepository(persistor, transactionRunner)

    private fun createAndSaveUser(): User = userRepository.saveUser(aUser())

    private fun createAndSaveBoard(
        name: String,
        user: User,
    ): Board =
        boardRepository.saveBoard(
            Board(
                id = randomUUID(),
                author = user,
                name = boardName(name),
                description = "",
                createdAt = storableNow(),
                updatedAt = storableNow(),
            )
        )

    // A board whose id is not in the store: the use case read and validated a board a concurrent
    // hard delete has since removed. Absence at the transition is an illegal state.
    private fun absentBoard(user: User): Board =
        Board(
            id = randomUUID(),
            author = user,
            name = boardName("Ghost"),
            description = "",
            createdAt = storableNow(),
            updatedAt = storableNow(),
        )

    private fun createAndSavePin(
        author: User,
        boards: List<Board> = emptyList(),
        createdAt: Instant = storableNow(),
        id: UUID = randomUUID(),
    ): Pin =
        pinRepository.savePin(
            Pin(
                id = id,
                author = author,
                sourceContextUrl = HttpUrl.parse("https://example.com"),
                sourceMediaUrl = HttpUrl.parse("https://example.com/media.jpeg"),
                description = "Something",
                tags = emptyList(),
                boards = boards,
                createdAt = createdAt,
                updatedAt = storableNow(),
            )
        )

    private fun saveMediaFor(pin: Pin) =
        mediaRepository.save(
            Media.StillImage(
                id = randomUUID(),
                pinId = pin.id,
                mimeType = "image/png",
                width = 1,
                height = 1,
                byteSize = 1,
                contentHash = "h",
                storageKey = "originals/x/${pin.id}/i.png",
                createdAt = storableNow(),
            )
        )

    @Test
    fun `Given active boards, Then findActiveBoardsForUser sorts them by name, Émile beside emile`() {
        // Given: code point order would put Émile after emma, lower-cased or not
        val user = createAndSaveUser()
        createAndSaveBoard("Émile", user)
        createAndSaveBoard("emma", user)
        createAndSaveBoard("emile", user)

        // When
        val names = boardRepository.findActiveBoardsForUser(user).map { it.name.text }

        // Then
        assertEquals(listOf("emile", "Émile", "emma"), names)
    }

    @Test
    fun `Given recycled boards, Then findRecycledBoardsForUser sorts them by name, Émile beside emile`() {
        // Given
        val user = createAndSaveUser()
        listOf("Émile", "emma", "emile").forEach {
            boardRepository.softDeleteBoard(createAndSaveBoard(it, user), storableNow())
        }

        // When
        val names = boardRepository.findRecycledBoardsForUser(user).map { it.name.text }

        // Then
        assertEquals(listOf("emile", "Émile", "emma"), names)
    }

    @Test
    fun `Given a soft-deleted board, Then it is excluded from active and included in recycled`() {
        // Given
        val user = createAndSaveUser()
        val activeBoard = createAndSaveBoard("Active", user)
        val recycledBoard = createAndSaveBoard("Recycled", user)

        // When
        val softDeleted = boardRepository.softDeleteBoard(recycledBoard, storableNow())

        // Then
        assertNotNull(softDeleted.softDeletedAt)
        assertEquals(listOf(activeBoard.id), boardRepository.findActiveBoardsForUser(user).map { it.id })
        assertEquals(listOf(recycledBoard.id), boardRepository.findRecycledBoardsForUser(user).map { it.id })
    }

    @Test
    fun `Given a deletion instant, Then softDeleteBoard stores it as both softDeletedAt and updatedAt`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val deletionInstant = Instant.parse("2026-01-02T03:04:05Z")

        // When
        boardRepository.softDeleteBoard(board, deletionInstant)

        // Then - the instant reaches the columns unchanged, read back from the store
        val stored = requireNotNull(boardRepository.findBoardById(board.id))
        assertEquals(deletionInstant, stored.softDeletedAt)
        assertEquals(deletionInstant, stored.updatedAt)
    }

    @Test
    fun `Given a recycled board, Then restore makes it active again`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val softDeleted = boardRepository.softDeleteBoard(board, storableNow())

        // When
        val restored = boardRepository.restoreBoard(softDeleted, storableNow())

        // Then
        assertNull(restored.softDeletedAt)
        assertEquals(listOf(board.id), boardRepository.findActiveBoardsForUser(user).map { it.id })
        assertTrue(boardRepository.findRecycledBoardsForUser(user).isEmpty())
    }

    @Test
    fun `Given a restoration instant, Then restoreBoard stores it as updatedAt and clears softDeletedAt`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val softDeleted = boardRepository.softDeleteBoard(board, storableNow())
        val restorationInstant = Instant.parse("2026-02-03T04:05:06Z")

        // When
        boardRepository.restoreBoard(softDeleted, restorationInstant)

        // Then - the instant reaches the column unchanged, read back from the store
        val stored = requireNotNull(boardRepository.findBoardById(board.id))
        assertEquals(restorationInstant, stored.updatedAt)
        assertNull(stored.softDeletedAt)
    }

    @Test
    fun `Given a board absent from the store, Then softDeleteBoard throws IllegalStateException naming its id`() {
        // Given - absence at the transition is an illegal state (concurrent hard delete), not a
        // missing argument, so it throws rather than NPE-ing on the null model
        val user = createAndSaveUser()
        val board = absentBoard(user)

        // When / Then
        val exception =
            assertThrows<IllegalStateException> {
                boardRepository.softDeleteBoard(board, storableNow())
            }
        assertTrue(exception.message!!.contains(board.id.toString()))
    }

    @Test
    fun `Given a board absent from the store, Then restoreBoard throws IllegalStateException naming its id`() {
        // Given - same illegal-state condition as softDeleteBoard on an absent row
        val user = createAndSaveUser()
        val board = absentBoard(user)

        // When / Then
        val exception =
            assertThrows<IllegalStateException> {
                boardRepository.restoreBoard(board, storableNow())
            }
        assertTrue(exception.message!!.contains(board.id.toString()))
    }

    @Test
    fun `Given a board with active and soft-deleted pins, Then countActivePinsInBoard counts only active`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        createAndSavePin(user, boards = listOf(board))
        val softDeletedPin = createAndSavePin(user, boards = listOf(board))
        pinRepository.softDeletePin(softDeletedPin, storableNow())

        // When
        val count = boardRepository.countActivePinsInBoard(board.id)

        // Then
        assertEquals(1, count)
    }

    @Test
    fun `Given a board with pins, Then permanentlyDeleteBoard removes it and its pin_board rows but leaves the pins`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val pin = createAndSavePin(user, boards = listOf(board))

        // When
        boardRepository.permanentlyDeleteBoard(board)

        // Then
        assertNull(boardRepository.findActiveBoardById(board.id))
        val reloadedPin = pinRepository.findPinById(pin.id)
        assertNotNull(reloadedPin)
        assertTrue(reloadedPin!!.boards.isEmpty())
    }

    @Test
    fun `Given recycled boards, Then permanentlyDeleteAllRecycledBoardsForUser clears them and their joins`() {
        // Given
        val user = createAndSaveUser()
        val recycledBoard1 = createAndSaveBoard("R1", user)
        val recycledBoard2 = createAndSaveBoard("R2", user)
        val activeBoard = createAndSaveBoard("Active", user)
        val pinInRecycledBoard = createAndSavePin(user, boards = listOf(recycledBoard1))
        boardRepository.softDeleteBoard(recycledBoard1, storableNow())
        boardRepository.softDeleteBoard(recycledBoard2, storableNow())

        // When
        boardRepository.permanentlyDeleteAllRecycledBoardsForUser(user)

        // Then
        assertNull(boardRepository.findBoardById(recycledBoard1.id))
        assertNull(boardRepository.findBoardById(recycledBoard2.id))
        assertNotNull(boardRepository.findActiveBoardById(activeBoard.id))
        val reloadedPin = pinRepository.findPinById(pinInRecycledBoard.id)
        assertNotNull(reloadedPin)
        assertTrue(reloadedPin!!.boards.isEmpty())
    }

    @Test
    fun `Given no recycled boards, Then permanentlyDeleteAllRecycledBoardsForUser is a no-op`() {
        // Given
        val user = createAndSaveUser()
        val activeBoard = createAndSaveBoard("Board", user)

        // When
        boardRepository.permanentlyDeleteAllRecycledBoardsForUser(user)

        // Then
        assertNotNull(boardRepository.findActiveBoardById(activeBoard.id))
    }

    @Test
    fun `Given an active board, Then findActiveBoardById returns it`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)

        // When
        val result = boardRepository.findActiveBoardById(board.id)

        // Then
        assertEquals(board, result)
    }

    @Test
    fun `Given findActiveBoardById on a recycled board, Then it returns null`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        boardRepository.softDeleteBoard(board, storableNow())

        // When
        val result = boardRepository.findActiveBoardById(board.id)

        // Then
        assertNull(result)
    }

    @Test
    fun `Given an active board, Then findBoardById returns it`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)

        // When
        val result = boardRepository.findBoardById(board.id)

        // Then
        assertEquals(board, result)
    }

    @Test
    fun `Given a recycled board, Then findBoardById still returns it`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        boardRepository.softDeleteBoard(board, storableNow())

        // When
        val result = boardRepository.findBoardById(board.id)

        // Then
        assertNotNull(result)
        assertEquals(board.id, result!!.id)
        assertNotNull(result.softDeletedAt)
    }

    @Test
    fun `Given an unknown board id, Then findBoardById returns null`() {
        // Given / When
        val result = boardRepository.findBoardById(randomUUID())

        // Then
        assertNull(result)
    }

    @Test
    fun `Given a stored board name the factory refuses, Then reading it throws rather than invent a name`() {
        // Given: a row no write of the API makes
        val user = createAndSaveUser()
        database
            .sqlUpdate(
                "insert into boards (id, author_id, name, description, created_at, updated_at) " +
                    "values (?, ?, ' ', '', ?, ?)"
            )
            .setParameter(1, randomUUID())
            .setParameter(2, user.id)
            .setParameter(3, storableNow())
            .setParameter(4, storableNow())
            .execute()

        // When / Then
        assertThrows<IllegalStateException> { boardRepository.findActiveBoardsForUser(user) }
    }

    // --- Names as identities ---

    @Test
    fun `Given a name held in another case outside ASCII, Then saveBoard throws BoardNameAlreadyTakenException`() {
        // Given: the column's collation folds case in every script, so the store sees these two names as one
        val user = createAndSaveUser()
        createAndSaveBoard("élodie", user)

        // When / Then
        assertThrows<BoardNameAlreadyTakenException> { createAndSaveBoard("ÉLODIE", user) }
    }

    @Test
    fun `Given two authors, Then each may hold the same board name`() {
        // Given: the index is scoped to the author, so a name is an identity per account
        val first = createAndSaveUser()
        val second = createAndSaveUser()
        createAndSaveBoard("voyage", first)

        // When
        val board = createAndSaveBoard("voyage", second)

        // Then
        assertEquals("voyage", board.name.text)
    }

    @Test
    fun `Given a recycled board, Then an active board cannot take its name`() {
        // Given: the index covers every row, so the recycle bin holds the name
        val user = createAndSaveUser()
        val board = createAndSaveBoard("voyage", user)
        boardRepository.softDeleteBoard(board, storableNow())

        // When / Then
        assertThrows<BoardNameAlreadyTakenException> { createAndSaveBoard("Voyage", user) }
    }

    @Test
    fun `Given an active board, Then findBoardForUserByName finds it in another case outside ASCII`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("élodie", user)

        // When
        val found = boardRepository.findBoardForUserByName(user = user, name = boardName("ÉLODIE"))

        // Then
        assertEquals(board.id, found?.id)
    }

    @Test
    fun `Given a recycled board, Then findBoardForUserByName still returns it`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("voyage", user)
        boardRepository.softDeleteBoard(board, storableNow())

        // When
        val found = boardRepository.findBoardForUserByName(user = user, name = boardName("Voyage"))

        // Then
        assertEquals(board.id, found?.id)
        assertNotNull(found?.softDeletedAt)
    }

    @Test
    fun `Given another author's board, Then findBoardForUserByName does not return it`() {
        // Given
        val owner = createAndSaveUser()
        val other = createAndSaveUser()
        createAndSaveBoard("voyage", owner)

        // When
        val found = boardRepository.findBoardForUserByName(user = other, name = boardName("voyage"))

        // Then
        assertNull(found)
    }

    // --- Creation timestamps ---

    @Test
    fun `Given a saved board, Then reading it back exposes its timestamps`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)

        // When
        val found = boardRepository.findBoardById(board.id)

        // Then
        assertNotNull(found?.createdAt)
        assertNotNull(found?.updatedAt)
    }

    // --- Cover ---

    @Test
    fun `Given newer pins recycled, media-less or filed elsewhere, Then findCoverPinId skips them`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val elsewhere = createAndSaveBoard("Elsewhere", user)
        val oldest = Instant.parse("2026-01-01T00:00:00Z")
        val filedElsewhere = createAndSavePin(user, listOf(elsewhere), createdAt = oldest.plusSeconds(3))
        val recycled = createAndSavePin(user, listOf(board), createdAt = oldest.plusSeconds(2))
        createAndSavePin(user, listOf(board), createdAt = oldest.plusSeconds(1))
        val cover = createAndSavePin(user, listOf(board), createdAt = oldest)
        listOf(filedElsewhere, recycled, cover).forEach { saveMediaFor(it) }
        pinRepository.softDeletePin(recycled, storableNow())

        // When
        val coverPinId = boardRepository.findCoverPinId(board.id)

        // Then
        assertEquals(cover.id, coverPinId)
    }

    @Test
    fun `Given two pins with a media, the older with the greater id, Then findCoverPinId answers the newer`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val older = Instant.parse("2026-01-01T00:00:00Z")
        val newerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val olderId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        saveMediaFor(createAndSavePin(user, boards = listOf(board), createdAt = older.plusSeconds(1), id = newerId))
        saveMediaFor(createAndSavePin(user, boards = listOf(board), createdAt = older, id = olderId))

        // When
        val coverPinId = boardRepository.findCoverPinId(board.id)

        // Then
        assertEquals(newerId, coverPinId)
    }

    @Test
    fun `Given two pins with a media sharing a createdAt, Then findCoverPinId answers the greater id`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val lower = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val greater = UUID.fromString("00000000-0000-0000-0000-000000000002")
        listOf(lower, greater).forEach { id ->
            saveMediaFor(createAndSavePin(user, boards = listOf(board), createdAt = createdAt, id = id))
        }

        // When
        val coverPinId = boardRepository.findCoverPinId(board.id)

        // Then
        assertEquals(greater, coverPinId)
    }

    @Test
    fun `Given a board whose pins all lack a media, Then findCoverPinId answers null`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)
        createAndSavePin(user, boards = listOf(board))

        // When, Then
        assertNull(boardRepository.findCoverPinId(board.id))
    }

    @Test
    fun `Given an empty board, Then findCoverPinId answers null`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard("Board", user)

        // When, Then
        assertNull(boardRepository.findCoverPinId(board.id))
    }
}
