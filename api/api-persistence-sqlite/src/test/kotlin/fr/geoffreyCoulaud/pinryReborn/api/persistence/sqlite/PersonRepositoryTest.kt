package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
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
            Person(id = randomUUID(), author = user, name = name, urls = urls, createdAt = storableNow())
        )

    private fun find(user: User, name: String, urls: List<String>): Person? =
        repository.findUserPerson(user = user, name = name, urls = urls)

    private fun storedUrls(person: Person): String? =
        database.sqlQuery("select urls from persons where id = ?").setParameter(person.id).findOne()?.getString("urls")

    @Test
    fun `Given addresses out of order and repeated, Then they are stored as a sorted and distinct JSON array`() {
        // When
        val person = savePerson(createAndSaveUser(), "Alice", listOf(SECOND_URL, FIRST_URL, SECOND_URL))

        // Then
        assertEquals("""["$FIRST_URL","$SECOND_URL"]""", storedUrls(person))
        assertEquals(listOf(FIRST_URL, SECOND_URL), person.urls)
    }

    @Test
    fun `Given no address, Then an empty JSON array is stored and none read back`() {
        // When
        val person = savePerson(createAndSaveUser(), "Alice", emptyList())

        // Then
        assertEquals("[]", storedUrls(person))
        assertEquals(emptyList<String>(), person.urls)
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
        // Given: no normalisation beyond the canonical order
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

    private companion object {
        const val FIRST_URL = "https://a.test/alice"
        const val SECOND_URL = "https://b.test/alice"
    }
}
