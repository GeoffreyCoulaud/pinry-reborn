package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonUrls
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PersonCreatorTest {
    private val clockInstant = Instant.parse("2026-10-08T10:00:00Z")
    private val repository = FoldingPersonRepository()
    private val creator =
        PersonCreator(
            personRepository = repository,
            transactionRunner = PassthroughTransactionRunner(),
            clock = clock(),
        )
    private val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)

    @Test
    fun `Given a person in another ASCII case and its addresses in another order, Then findOrCreate finds it`() {
        // Given
        val existing = creator.findOrCreate(name = "Alice", urls = listOf(FIRST_URL, SECOND_URL), user = user)

        // When
        val found = creator.findOrCreate(name = "aLICE", urls = listOf(SECOND_URL, FIRST_URL), user = user)

        // Then
        assertEquals(existing, found)
        assertEquals(1, repository.rowCount)
    }

    @Test
    fun `Given a person, Then findOrCreate with one address more creates a second row`() {
        // Given
        val existing = creator.findOrCreate(name = "Alice", urls = listOf(FIRST_URL), user = user)

        // When
        val created = creator.findOrCreate(name = "Alice", urls = listOf(FIRST_URL, SECOND_URL), user = user)

        // Then
        assertNotEquals(existing.id, created.id)
        assertEquals(2, repository.rowCount)
    }

    @Test
    fun `Given no such person, Then findOrCreate stamps the new row from the clock with canonical addresses`() {
        // When
        val created = creator.findOrCreate(name = "Alice", urls = listOf(SECOND_URL, FIRST_URL), user = user)

        // Then
        assertEquals(clockInstant, created.createdAt)
        assertEquals(PersonUrls.of(listOf(FIRST_URL, SECOND_URL)), created.urls)
    }

    private fun clock() =
        object : Clock {
            override fun now(): Instant = clockInstant
        }

    private class PassthroughTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    /** Folds the name as `ix_persons_author_name_nocase_urls` does, on the ASCII names these tests use. */
    private class FoldingPersonRepository : PersonRepositoryInterface {
        private val rows = mutableMapOf<String, Person>()

        val rowCount: Int
            get() = rows.size

        override fun savePerson(person: Person): Person = person.also {
            rows[key(person.author, person.name, person.urls)] = it
        }

        override fun findUserPerson(user: User, name: String, urls: PersonUrls): Person? = rows[key(user, name, urls)]

        private fun key(user: User, name: String, urls: PersonUrls) = "${user.id}:${name.lowercase()}:${urls.joined}"
    }

    private companion object {
        const val FIRST_URL = "https://a.test/alice"
        const val SECOND_URL = "https://b.test/alice"
    }
}
