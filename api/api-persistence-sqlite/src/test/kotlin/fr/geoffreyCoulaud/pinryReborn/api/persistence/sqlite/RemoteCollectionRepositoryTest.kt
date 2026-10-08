package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QRemoteCollectionModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.BoardRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.RemoteCollectionRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RemoteCollectionRepositoryTest : RepositoryTest() {
    private val repository = RemoteCollectionRepository(persistor)
    private val boardRepository = BoardRepository(persistor)
    private val userRepository = UserRepository(persistor)

    private fun createAndSaveUser(): User =
        userRepository.saveUser(User(id = randomUUID(), name = createRandomString(), createdAt = storableNow()))

    private fun createAndSaveBoard(user: User): Board =
        boardRepository.saveBoard(
            Board(
                id = randomUUID(),
                author = user,
                name = createRandomString(),
                description = "",
                createdAt = storableNow(),
                updatedAt = storableNow(),
            )
        )

    private fun link(board: Board, url: String = "https://remote.test/${createRandomString()}"): RemoteCollection =
        repository.saveRemoteCollection(
            RemoteCollection(
                id = randomUUID(),
                author = board.author,
                url = url,
                name = createRandomString(),
                board = board,
                createdAt = storableNow(),
            )
        )

    private fun storedUrls(): List<String> = QRemoteCollectionModel().findList().map { it.url }.sorted()

    @Test
    fun `Given a linked collection, Then findUserRemoteCollectionByUrl reads it back equal`() {
        // Given
        val board = createAndSaveBoard(createAndSaveUser())
        val collection = link(board)

        // When
        val found = repository.findUserRemoteCollectionByUrl(board.author, collection.url)

        // Then
        assertEquals(collection, found)
    }

    @Test
    fun `Given another author's collection, Then findUserRemoteCollectionByUrl does not return it`() {
        // Given
        val collection = link(createAndSaveBoard(createAndSaveUser()))

        // When
        val found = repository.findUserRemoteCollectionByUrl(createAndSaveUser(), collection.url)

        // Then
        assertNull(found)
    }

    @Test
    fun `Given a collection, Then findUserRemoteCollectionByUrl misses its address with a trailing slash`() {
        // Given
        val collection = link(createAndSaveBoard(createAndSaveUser()))

        // When
        val found = repository.findUserRemoteCollectionByUrl(collection.author, collection.url + "/")

        // Then
        assertNull(found)
    }

    @Test
    fun `Given two collections linked to one board, Then both are stored`() {
        // Given
        val board = createAndSaveBoard(createAndSaveUser())
        val first = link(board)
        val second = link(board)

        // When
        val urls = storedUrls()

        // Then
        assertEquals(listOf(first.url, second.url).sorted(), urls)
    }

    @Test
    fun `Given a board recycled then restored, Then its collection is still linked to it`() {
        // Given
        val board = createAndSaveBoard(createAndSaveUser())
        val collection = link(board)

        // When
        boardRepository.softDeleteBoard(board, storableNow())
        val whileRecycled = repository.findUserRemoteCollectionByUrl(board.author, collection.url)
        boardRepository.restoreBoard(board, storableNow())
        val afterRestore = repository.findUserRemoteCollectionByUrl(board.author, collection.url)

        // Then
        assertEquals(board.id, whileRecycled?.board?.id)
        assertEquals(board.id, afterRestore?.board?.id)
    }

    @Test
    fun `Given a board deleted permanently, Then its collections go and another board's stay`() {
        // Given
        val user = createAndSaveUser()
        val deleted = createAndSaveBoard(user)
        val kept = link(createAndSaveBoard(user))
        link(deleted)

        // When
        // `foreign_keys` is off, so the store would not refuse a collection row left behind.
        boardRepository.permanentlyDeleteBoard(deleted)

        // Then
        assertEquals(listOf(kept.url), storedUrls())
    }

    @Test
    fun `Given the recycle bin emptied, Then the recycled boards' collections go and an active board's stay`() {
        // Given
        val user = createAndSaveUser()
        val recycled = createAndSaveBoard(user)
        val kept = link(createAndSaveBoard(user))
        link(recycled)
        boardRepository.softDeleteBoard(recycled, storableNow())

        // When
        boardRepository.permanentlyDeleteAllRecycledBoardsForUser(user)

        // Then
        assertEquals(listOf(kept.url), storedUrls())
    }

    @Test
    fun `Given every board of a user deleted, Then their collections go and another user's stay`() {
        // Given
        val user = createAndSaveUser()
        val recycled = createAndSaveBoard(user)
        link(createAndSaveBoard(user))
        link(recycled)
        boardRepository.softDeleteBoard(recycled, storableNow())
        val kept = link(createAndSaveBoard(createAndSaveUser()))

        // When
        boardRepository.permanentlyDeleteAllBoardsForUser(user)

        // Then
        assertEquals(listOf(kept.url), storedUrls())
    }
}
