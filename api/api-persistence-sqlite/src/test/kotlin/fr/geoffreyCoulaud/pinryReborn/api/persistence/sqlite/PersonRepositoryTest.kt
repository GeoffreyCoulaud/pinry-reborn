package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.PersonRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import jakarta.persistence.PersistenceException
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PersonRepositoryTest : RepositoryTest() {
    private val repository = PersonRepository(persistor)
    private val userRepository = UserRepository(persistor)

    private fun createAndSaveUser(): User =
        userRepository.saveUser(User(id = randomUUID(), name = createRandomString(), createdAt = storableNow()))

    private fun savePerson(user: User, name: String, urls: List<String>): Person =
        repository.savePerson(
            Person(
                id = randomUUID(),
                author = user,
                name = name(name),
                urls = addresses(urls),
                createdAt = storableNow(),
            )
        )

    private fun find(user: User, name: String, urls: List<String>): Person? =
        repository.findUserPerson(user = user, name = name(name), urls = addresses(urls))

    private fun name(text: String) = checkNotNull(PersonName.parse(text))

    private fun addresses(texts: List<String>) = texts.map { checkNotNull(HttpUrl.parse(it)) }.toSet()

    private fun storedUrls(person: Person): String? =
        database.sqlQuery("select urls from persons where id = ?").setParameter(person.id).findOne()?.getString("urls")

    @Test
    fun `Given addresses out of order and repeated, Then they are stored as a sorted and distinct JSON array`() {
        // When
        val person = savePerson(createAndSaveUser(), "Alice", listOf(SECOND_URL, FIRST_URL, SECOND_URL))

        // Then
        assertEquals("""["$FIRST_URL","$SECOND_URL"]""", storedUrls(person))
        assertEquals(addresses(listOf(FIRST_URL, SECOND_URL)), person.urls)
    }

    @Test
    fun `Given no address, Then an empty JSON array is stored and none read back`() {
        // When
        val person = savePerson(createAndSaveUser(), "Alice", emptyList())

        // Then
        assertEquals("[]", storedUrls(person))
        assertEquals(emptySet<HttpUrl>(), person.urls)
    }

    @Test
    fun `Given a saved person, Then findUserPerson reads it back equal`() {
        // Given
        val user = createAndSaveUser()
        val person = savePerson(user, "Alice", listOf(FIRST_URL, SECOND_URL))

        // When
        val found = find(user, "Alice", listOf(FIRST_URL, SECOND_URL))

        // Then
        assertEquals(person, found)
    }

    @Test
    fun `Given a saved person, Then findUserPerson finds it in another ASCII case and address order`() {
        // Given
        val user = createAndSaveUser()
        val person = savePerson(user, "Alice", listOf(FIRST_URL, SECOND_URL))

        // When
        val found = find(user, "aLICE", listOf(SECOND_URL, FIRST_URL))

        // Then
        assertEquals(person.id, found?.id)
    }

    @Test
    fun `Given a saved person, Then findUserPerson misses it with one address more`() {
        // Given
        val user = createAndSaveUser()
        savePerson(user, "Alice", listOf(FIRST_URL))

        // When
        val found = find(user, "Alice", listOf(FIRST_URL, SECOND_URL))

        // Then
        assertNull(found)
    }

    @Test
    fun `Given a saved person, Then findUserPerson misses it with its address's trailing slash`() {
        // Given: an address keeps its trailing slash
        val user = createAndSaveUser()
        savePerson(user, "Alice", listOf(FIRST_URL))

        // When
        val found = find(user, "Alice", listOf("$FIRST_URL/"))

        // Then
        assertNull(found)
    }

    @Test
    fun `Given another author's person of the same name, Then findUserPerson misses it`() {
        // Given
        val user = createAndSaveUser()
        savePerson(createAndSaveUser(), "Alice", listOf(FIRST_URL))

        // When
        val found = find(user, "Alice", listOf(FIRST_URL))

        // Then
        assertNull(found)
    }

    @Test
    fun `Given two homonyms with different addresses, Then the store holds both`() {
        // Given
        val user = createAndSaveUser()
        savePerson(user, "Alice", listOf(FIRST_URL))

        // When
        val homonym = savePerson(user, "Alice", listOf(SECOND_URL))

        // Then
        assertEquals(homonym, find(user, "Alice", listOf(SECOND_URL)))
    }

    @Test
    fun `Given a person held up to ASCII case and address order, Then saving it again is refused by the store`() {
        // Given: no translation, deliberately. PersonCreator reads through the same identity first.
        val user = createAndSaveUser()
        savePerson(user, "Alice", listOf(FIRST_URL, SECOND_URL))

        // When / Then
        assertThrows<PersistenceException> { savePerson(user, "ALICE", listOf(SECOND_URL, FIRST_URL)) }
    }

    @Test
    fun `Given persons of two users, Then findAllPersonsForUser lists only the user's`() {
        // Given
        val user = createAndSaveUser()
        val alice = savePerson(user, "Alice", listOf(FIRST_URL))
        val bob = savePerson(user, "Bob", emptyList())
        savePerson(createAndSaveUser(), "Carol", listOf(SECOND_URL))

        // When
        val found = repository.findAllPersonsForUser(user)

        // Then
        assertEquals(setOf(alice, bob), found.toSet())
    }

    @Test
    fun `Given three persons, Then findPersonsByIds reads back the two asked for`() {
        // Given
        val user = createAndSaveUser()
        val alice = savePerson(user, "Alice", listOf(FIRST_URL))
        val bob = savePerson(user, "Bob", emptyList())
        savePerson(user, "Carol", listOf(SECOND_URL))

        // When
        val found = repository.findPersonsByIds(setOf(alice.id, bob.id))

        // Then
        assertEquals(setOf(alice, bob), found.toSet())
    }

    @Test
    fun `Given persons of two users, Then deleteAllPersonsForUser removes only the user's`() {
        // Given
        val user = createAndSaveUser()
        val otherUser = createAndSaveUser()
        savePerson(user, "Alice", listOf(FIRST_URL))
        val kept = savePerson(otherUser, "Alice", listOf(FIRST_URL))

        // When
        repository.deleteAllPersonsForUser(user)

        // Then
        assertNull(find(user, "Alice", listOf(FIRST_URL)))
        assertEquals(kept, find(otherUser, "Alice", listOf(FIRST_URL)))
    }

    // --- Matching ---

    private fun matching(user: User, query: String, limit: Int = 10) =
        repository.findPersonsForUserMatching(user = user, query = query, limit = limit)

    @Test
    fun `Given a name beginning with the term and one merely holding it, Then the first is matched first`() {
        // Given
        val user = createAndSaveUser()
        val contained = savePerson(user, "Malice", emptyList())
        val prefixed = savePerson(user, "Alice", emptyList())
        savePerson(user, "Bob", emptyList())

        // When
        val found = matching(user, "alice")

        // Then
        assertEquals(listOf(prefixed, contained), found)
    }

    @Test
    fun `Given two homonyms with different addresses, Then both are matched with their addresses`() {
        // Given
        val user = createAndSaveUser()
        val first = savePerson(user, "Alice", listOf(FIRST_URL))
        val second = savePerson(user, "Alice", listOf(SECOND_URL))

        // When
        val found = matching(user, "Alice")

        // Then
        assertEquals(setOf(first, second), found.toSet())
    }

    @Test
    fun `Given more prefix matches than the limit, Then findPersonsForUserMatching serves only those`() {
        // Given
        val user = createAndSaveUser()
        savePerson(user, "Alice", listOf(FIRST_URL))
        savePerson(user, "Alice", listOf(SECOND_URL))
        savePerson(user, "Malice", emptyList())

        // When
        val found = matching(user, "Alice", limit = 2)

        // Then
        assertEquals(listOf("Alice", "Alice"), found.map { it.name.text })
    }

    @Test
    fun `Given a limit of zero, Then findPersonsForUserMatching serves nothing`() {
        // Given: Ebean's setMaxRows reads zero as unbounded
        val user = createAndSaveUser()
        savePerson(user, "Alice", emptyList())

        // When
        val found = matching(user, "Alice", limit = 0)

        // Then
        assertEquals(emptyList<Person>(), found)
    }

    @Test
    fun `Given another author's matching person, Then findPersonsForUserMatching leaves it out`() {
        // Given
        val user = createAndSaveUser()
        val own = savePerson(user, "Alice", emptyList())
        savePerson(createAndSaveUser(), "Alice", emptyList())

        // When
        val found = matching(user, "Alice")

        // Then
        assertEquals(listOf(own), found)
    }

    private companion object {
        const val FIRST_URL = "https://a.test/alice"
        const val SECOND_URL = "https://b.test/alice"
    }
}
