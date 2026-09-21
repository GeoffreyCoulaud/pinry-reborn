package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.SearchEmptyQueryError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID.randomUUID

class TagSearcherTest {
    private val tagRepository = mockk<TagRepositoryInterface>()
    private val useCase = TagSearcher(tagRepository = tagRepository)

    private fun createUser() = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)

    private fun createTag(user: User, name: String) = Tag(id = randomUUID(), author = user, name = name,
        createdAt = TestTime.now)

    @Test
    fun `Given empty query, Then throws SearchEmptyQueryError`() {
        // Given
        val user = createUser()

        // When, Then
        assertThrows<SearchEmptyQueryError> {
            useCase.searchTags(user = user, query = "", limit = 10)
        }
    }

    @Test
    fun `Given blank query, Then throws SearchEmptyQueryError`() {
        // Given
        val user = createUser()

        // When, Then
        assertThrows<SearchEmptyQueryError> {
            useCase.searchTags(user = user, query = "   ", limit = 10)
        }
    }

    @Test
    fun `Given a query, Then returns what the repository matched, in its order`() {
        // Given
        val user = createUser()
        val matched = listOf(createTag(user, "scapegoat"), createTag(user, "landscape"))
        every {
            tagRepository.findTagsForUserMatching(user = user, query = "scape", limit = 8)
        } returns matched

        // When
        val results = useCase.searchTags(user = user, query = "scape", limit = 8)

        // Then: the order is the repository's, prefix matches ahead of the rest
        assertEquals(matched, results)
    }
}
