package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonCreatorTest {
    private val clockInstant = Instant.parse("2026-10-08T10:00:00Z")
    private val repository = RecordingPersonRepository()
    private val creator =
        PersonCreator(
            personRepository = repository,
            transactionRunner = PassthroughTransactionRunner(),
            clock = clock(),
        )
    private val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)

    @Test
    fun `Given a person the store finds, Then findOrCreate returns it and saves nothing`() {
        // Given
        val existing = Person(id = randomUUID(), author = user, name = NAME, urls = URLS, createdAt = TestTime.now)
        repository.found = existing

        // When
        val found = creator.findOrCreate(name = NAME, urls = URLS, user = user)

        // Then
        assertEquals(existing, found)
        assertEquals(emptyList<Person>(), repository.saved)
    }

    @Test
    fun `Given no such person, Then findOrCreate saves one stamped from the clock`() {
        // When
        val created = creator.findOrCreate(name = NAME, urls = URLS, user = user)

        // Then
        assertEquals(listOf(created), repository.saved)
        assertEquals(clockInstant, created.createdAt)
        assertEquals(URLS, created.urls)
    }

    private fun clock() =
        object : Clock {
            override fun now(): Instant = clockInstant
        }

    private class PassthroughTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    /** The identity itself is the store's, held by PersonRepositoryTest against SQLite. */
    private class RecordingPersonRepository : PersonRepositoryInterface {
        var found: Person? = null
        val saved = mutableListOf<Person>()

        override fun savePerson(person: Person): Person = person.also { saved += it }

        override fun findUserPerson(user: User, name: PersonName, urls: Set<HttpUrl>): Person? = found

        override fun findPersonsForUserMatching(user: User, query: String, limit: Int) =
            error("A creation searches no person")

        override fun findUserPersonsByIds(user: User, ids: Set<UUID>) = error("A creation reads no person back")

        override fun findAllPersonsForUser(user: User) = error("A creation lists no person")

        override fun deleteAllPersonsForUser(user: User) = error("A creation deletes no person")
    }

    private companion object {
        val NAME = checkNotNull(PersonName.parse("Alice"))
        val URLS = setOf(checkNotNull(HttpUrl.parse("https://a.test/alice")))
    }
}
