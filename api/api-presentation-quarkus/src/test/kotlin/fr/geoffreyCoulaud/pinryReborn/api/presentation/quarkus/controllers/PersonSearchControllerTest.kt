package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonSearcher
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.quarkus.security.identity.SecurityIdentity
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonSearchControllerTest {
    private val personSearcher = mockk<PersonSearcher>()
    private val securityIdentity = mockk<SecurityIdentity>()
    private val controller =
        PersonSearchController(personSearcher = personSearcher, securityIdentity = securityIdentity)
    private val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)

    @Test
    fun `Given no limit, Then the searcher is asked the tag search's default and the addresses are answered sorted`() {
        // Given
        val query = createRandomString()
        val person =
            Person(
                id = randomUUID(),
                author = user,
                name = checkNotNull(PersonName.parse("Alice")),
                urls = listOf(LATER_URL, URL).map { checkNotNull(HttpUrl.parse(it)) }.toSet(),
                createdAt = TestTime.now,
            )
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            personSearcher.searchPersons(user = user, query = query, limit = TagSearchController.DEFAULT_LIMIT)
        } returns listOf(person)

        // When
        val response = controller.searchPersons(query = query, limitParam = null)

        // Then
        assertEquals(
            listOf(PersonOutputDto(name = "Alice", urls = listOf(URL, LATER_URL))),
            response.entity.results.map { it.person },
        )
    }

    @Test
    fun `Given a limit above the max and no query, Then the searcher is asked a blank term at the max`() {
        // Given: the use case is what refuses the blank term
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            personSearcher.searchPersons(user = user, query = "", limit = TagSearchController.MAX_LIMIT)
        } returns emptyList()

        // When
        val response = controller.searchPersons(query = null, limitParam = TagSearchController.MAX_LIMIT + 5)

        // Then
        assertEquals(200, response.status)
    }

    private companion object {
        const val URL = "https://a.test/alice"
        const val LATER_URL = "https://b.test/alice"
    }
}
