package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.usecases.TagSearcher
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.quarkus.security.identity.SecurityIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class TagSearchControllerTest {
    private val tagSearcher = mockk<TagSearcher>()
    private val securityIdentity = mockk<SecurityIdentity>()
    private val controller = TagSearchController(
        tagSearcher = tagSearcher,
        securityIdentity = securityIdentity,
    )

    @Test
    fun `Given no limit and a query, Then searchTags uses the default limit`() {
        // Given
        val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val query = createRandomString()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            tagSearcher.searchTags(user = user, query = query, limit = TagSearchController.DEFAULT_LIMIT)
        } returns emptyList()

        // When
        val response = controller.searchTags(query = query, limitParam = null)

        // Then
        assertEquals(200, response.status)
    }

    @Test
    fun `Given a limit above the max and no query, Then the searcher is asked a blank term at the max limit`() {
        // Given: an absent `q` is a caller that did not mean to search, and the use case is what refuses it
        val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            tagSearcher.searchTags(user = user, query = "", limit = TagSearchController.MAX_LIMIT)
        } returns emptyList()

        // When
        val response = controller.searchTags(query = null, limitParam = TagSearchController.MAX_LIMIT + 5)

        // Then
        assertEquals(200, response.status)
    }
}
