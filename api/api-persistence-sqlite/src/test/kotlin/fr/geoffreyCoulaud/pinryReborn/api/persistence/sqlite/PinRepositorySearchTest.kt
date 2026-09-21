package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.PinQueries
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.matchingText
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

/**
 * The catalogue's `q`: what a term matches, what it does not, and the junction that has to stay
 * closed for the second page of a search to still be a search.
 */
class PinRepositorySearchTest : PinRepositoryFixtures() {
    private fun savePin(
        author: User,
        description: String = createRandomString(),
        tags: List<Tag> = emptyList(),
        boards: List<Board> = emptyList(),
        createdAt: Instant = storableNow(),
    ): Pin =
        repository.savePin(
            Pin(
                id = randomUUID(),
                author = author,
                sourceContextUrl = "https://example.com",
                sourceMediaUrl = "https://example.com/image.jpeg",
                description = description,
                tags = tags,
                boards = boards,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )

    private fun search(
        author: User,
        query: String,
        cursor: Cursor? = null,
        pageSize: Int = 20,
    ): Page<Pin> =
        repository.findPinsForUser(
            reader = author,
            cursor = cursor,
            pageSize = pageSize,
            sortStrategy = PinSortStrategy.CREATED_AT_ASC,
            query = query,
        )

    @Test
    fun `Given a tagged pin, a described pin and a third matching neither, Then the page holds the first two`() {
        // Given
        val author = createAndSaveUser()
        val tagged = savePin(author, description = "A photograph", tags = listOf(createAndSaveTag("cat", author)))
        val described = savePin(author, description = "A cat on a wall")
        savePin(author, description = "A dog", tags = listOf(createAndSaveTag("puppy", author)))

        // When
        val found = search(author, "cat")

        // Then: the tag is the metadata a pin board actually carries, so it matches beside the description
        assertEquals(setOf(tagged.id, described.id), found.items.map { it.id }.toSet())
    }

    @Test
    fun `Given a matching pin outside the board, Then the board's page leaves it out`() {
        // Given
        val author = createAndSaveUser()
        val board = createAndSaveBoard(author)
        val inside = savePin(author, description = "A cat indoors", boards = listOf(board))
        savePin(author, description = "A cat elsewhere")

        // When
        val found = repository.findActivePinsForBoard(
            reader = author,
            boardId = board.id,
            cursor = null,
            pageSize = 20,
            sortStrategy = PinSortStrategy.CREATED_AT_ASC,
            query = "cat",
        )

        // Then
        assertEquals(listOf(inside.id), found.items.map { it.id })
    }

    @Test
    fun `Given a tag stored Cat, Then a lower-case term finds it`() {
        // Given: SQLite's LIKE folds the English alphabet, which is the fold the tag index defines
        val author = createAndSaveUser()
        val pin = savePin(author, description = "A photograph", tags = listOf(createAndSaveTag("Cat", author)))

        // When
        val found = search(author, "cat")

        // Then
        assertEquals(listOf(pin.id), found.items.map { it.id })
    }

    @Test
    fun `Given a description holding cafe accented, Then the accented term finds it and the bare one does not`() {
        // Given
        val author = createAndSaveUser()
        val pin = savePin(author, description = "Un café serré")

        // When
        val accented = search(author, "café")
        val bare = search(author, "cafe")

        // Then: no accent folding, and this assertion is what says so the day someone adds it
        assertEquals(listOf(pin.id), accented.items.map { it.id })
        assertTrue(bare.items.isEmpty(), "${bare.items}")
    }

    @Test
    fun `Given a first page of a search, Then the second page read with its cursor holds only matching pins`() {
        // Given: matching and non-matching pins alternate, so an open junction lets the others back in
        val author = createAndSaveUser()
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val first = savePin(author, description = "A cat, first", createdAt = start)
        savePin(author, description = "A dog", createdAt = start.plusSeconds(1))
        val second = savePin(author, description = "A cat, second", createdAt = start.plusSeconds(2))
        savePin(author, description = "A horse", createdAt = start.plusSeconds(3))

        // When
        val firstPage = search(author, "cat", pageSize = 1)
        val secondPage = search(author, "cat", cursor = firstPage.nextCursor, pageSize = 1)

        // Then
        assertEquals(listOf(first.id), firstPage.items.map { it.id })
        assertNotNull(firstPage.nextCursor)
        assertEquals(listOf(second.id), secondPage.items.map { it.id })
    }

    @Test
    fun `Given the text predicate as Ebean builds it, Then its plan reads the tags through a subquery`() {
        // Given: the query the repository runs, executed so Ebean records the SQL it generated
        val author = createAndSaveUser()
        val query = PinQueries.active().author.id.equalTo(author.id).matchingText("cat")
        query.findList()

        // When
        val plan =
            database
                .sqlQuery("explain query plan ${query.query().generatedSql}")
                .setParameter(1, author.id.toString())
                .setParameter(2, "%cat%")
                .setParameter(3, "%cat%")
                .findList()
                .joinToString("\n") { "${it["detail"]}" }

        // Then: the pin table is scanned here by design, so the absence of SCAN would prove nothing
        assertTrue(plan.contains("SUBQUERY"), plan)
    }
}
