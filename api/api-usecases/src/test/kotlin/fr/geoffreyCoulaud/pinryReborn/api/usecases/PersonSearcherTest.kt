package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.SearchEmptyQueryError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PersonSearcherTest {
    private val personRepository = mockk<PersonRepositoryInterface>()
    private val useCase = PersonSearcher(personRepository = personRepository)

    private fun createUser() = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)

    private fun createPerson(user: User, name: String) =
        Person(
            id = randomUUID(),
            author = user,
            name = checkNotNull(PersonName.parse(name)),
            urls = emptySet(),
            createdAt = TestTime.now,
        )

    @Test
    fun `Given a blank query, Then searchPersons throws SearchEmptyQueryError`() {
        // Given
        val user = createUser()

        // When, Then
        assertThrows<SearchEmptyQueryError> { useCase.searchPersons(user = user, query = "   ", limit = 10) }
    }

    @Test
    fun `Given a query, Then searchPersons returns what the repository matched, in its order`() {
        // Given
        val user = createUser()
        val matched = listOf(createPerson(user, "Alice"), createPerson(user, "Malice"))
        every { personRepository.findPersonsForUserMatching(user = user, query = "alice", limit = 8) } returns matched

        // When
        val results = useCase.searchPersons(user = user, query = "alice", limit = 8)

        // Then
        assertEquals(matched, results)
    }
}
