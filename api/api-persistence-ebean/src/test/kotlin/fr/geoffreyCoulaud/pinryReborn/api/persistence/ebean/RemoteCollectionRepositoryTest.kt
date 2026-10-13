package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.boardName
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.collectionName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QRemoteCollectionModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.BoardRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.RemoteCollectionRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RemoteCollectionRepositoryTest : RepositoryTest() {
    private val repository = RemoteCollectionRepository(persistor)
    private val boardRepository = BoardRepository(persistor)
    private val userRepository = UserRepository(persistor)

    private fun createAndSaveUser(): User = userRepository.saveUser(aUser())

    private fun createAndSaveBoard(user: User): Board =
        boardRepository.saveBoard(
            Board(
                id = randomUUID(),
                author = user,
                name = boardName(createRandomString()),
                description = "",
                createdAt = storableNow(),
                updatedAt = storableNow(),
            )
        )

    private fun link(board: Board, name: String = createRandomString()): RemoteCollection =
        repository.saveRemoteCollection(
            RemoteCollection(
                id = randomUUID(),
                author = board.author,
                url = checkNotNull(HttpUrl.parse("https://remote.test/${createRandomString()}")),
                name = collectionName(name),
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
        val found =
            repository.findUserRemoteCollectionByUrl(
                collection.author,
                checkNotNull(HttpUrl.parse("${collection.url}/")),
            )

        // Then
        assertNull(found)
    }

    @Test
    fun `Given collections on an active and a recycled board, Then findAll returns both and no other user's`() {
        // Given
        val user = createAndSaveUser()
        val recycled = createAndSaveBoard(user)
        val onActive = link(createAndSaveBoard(user))
        val onRecycled = link(recycled)
        boardRepository.softDeleteBoard(recycled, storableNow())
        link(createAndSaveBoard(createAndSaveUser()))

        // When
        val found = repository.findAllRemoteCollectionsForUser(user)

        // Then
        assertEquals(setOf(onActive.url, onRecycled.url), found.map { it.url }.toSet())
        assertEquals(recycled.id, found.single { it.url == onRecycled.url }.board.id)
    }

    @Test
    fun `Given collections on two boards, Then one board's are sorted by name, Émile beside emile`() {
        // Given: code point order would put Émile after emma, lower-cased or not
        val user = createAndSaveUser()
        val board = createAndSaveBoard(user)
        val accented = link(board, name = "Émile")
        val emma = link(board, name = "emma")
        val bare = link(board, name = "emile")
        link(createAndSaveBoard(user))

        // When
        val found = repository.findRemoteCollectionsForBoard(board.id)

        // Then
        assertEquals(listOf(bare, accented, emma), found)
    }

    @Test
    fun `Given two collections of one name in two cases, Then findRemoteCollectionsForBoard orders them by id`() {
        // Given: the collation reads the two names as one, so the id breaks the tie
        val user = createAndSaveUser()
        val board = createAndSaveBoard(user)
        val collections = listOf(link(board, name = "élodie"), link(board, name = "ÉLODIE"))

        // When
        val found = repository.findRemoteCollectionsForBoard(board.id)

        // Then: PostgreSQL orders a uuid by its bytes, as its text sorts, where Java's compareTo is signed
        assertEquals(collections.sortedBy { it.id.toString() }, found)
    }

    @Test
    fun `Given a stored collection name the factory refuses, Then reading it throws rather than invent a name`() {
        // Given: a row no write of the API makes
        val board = createAndSaveBoard(createAndSaveUser())
        database
            .sqlUpdate(
                "insert into remote_collections (id, author_id, created_at, board_id, url, name) " +
                    "values (?, ?, ?, ?, 'https://remote.test/blank', ' ')"
            )
            .setParameter(1, randomUUID())
            .setParameter(2, board.author.id)
            .setParameter(3, storableNow())
            .setParameter(4, board.id)
            .execute()

        // When / Then
        assertThrows<IllegalStateException> { repository.findRemoteCollectionsForBoard(board.id) }
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
        assertEquals(listOf(first.url, second.url).map { it.toString() }.sorted(), urls)
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
        boardRepository.permanentlyDeleteBoard(deleted)

        // Then
        assertEquals(listOf(kept.url.toString()), storedUrls())
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
        assertEquals(listOf(kept.url.toString()), storedUrls())
    }
}
