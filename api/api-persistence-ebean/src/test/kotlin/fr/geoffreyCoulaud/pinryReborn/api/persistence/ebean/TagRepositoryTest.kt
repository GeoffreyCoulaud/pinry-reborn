package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.tagName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.TagRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.UserRepository
import jakarta.persistence.PersistenceException
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TagRepositoryTest : RepositoryTest() {
    private val repository = TagRepository(persistor)
    private val userRepository = UserRepository(persistor)

    private fun createAndSaveUser(): User = userRepository.saveUser(aUser())

    @Test
    fun `Given a new tag, Then saveTag persists it`() {
        // Given
        val user = createAndSaveUser()
        val tag = Tag(id = randomUUID(), author = user, name = tagName("landscape"), createdAt = storableNow())

        // When
        val saved = repository.saveTag(tag)

        // Then
        assertEquals(tag.id, saved.id)
        assertEquals(tag.name, saved.name)
    }

    @Test
    fun `Given a tag owned by the user, Then findUserTagByName returns it`() {
        // Given
        val user = createAndSaveUser()
        val tag =
            repository.saveTag(
                Tag(id = randomUUID(), author = user, name = tagName("landscape"), createdAt = storableNow())
            )

        // When
        val found = repository.findUserTagByName(user = user, name = tagName("landscape"))

        // Then
        assertNotNull(found)
        assertEquals(tag.id, found!!.id)
    }

    @Test
    fun `Given no tag with the given name, Then findUserTagByName returns null`() {
        // Given
        val user = createAndSaveUser()

        // When
        val found = repository.findUserTagByName(user = user, name = tagName("nonexistent"))

        // Then
        assertNull(found)
    }

    @Test
    fun `Given tags owned by the user, Then findAllTagsForUser returns them`() {
        // Given
        val user = createAndSaveUser()
        val tag1 =
            repository.saveTag(Tag(id = randomUUID(), author = user, name = tagName("tag1"), createdAt = storableNow()))
        val tag2 =
            repository.saveTag(Tag(id = randomUUID(), author = user, name = tagName("tag2"), createdAt = storableNow()))

        // When
        val tags = repository.findAllTagsForUser(user)

        // Then
        assertEquals(setOf(tag1, tag2), tags.toSet())
    }

    @Test
    fun `Given a stored tag name the factory refuses, Then reading it throws rather than invent a name`() {
        // Given: a row no write of the API makes
        val user = createAndSaveUser()
        database
            .sqlUpdate("insert into tags (id, author_id, name, created_at) values (?, ?, ' ', ?)")
            .setParameter(1, randomUUID())
            .setParameter(2, user.id)
            .setParameter(3, storableNow())
            .execute()

        // When / Then
        assertThrows<IllegalStateException> { repository.findAllTagsForUser(user) }
    }

    // --- Names as identities ---

    private fun saveTag(name: String, user: User) =
        repository.saveTag(Tag(id = randomUUID(), author = user, name = tagName(name), createdAt = storableNow()))

    @Test
    fun `Given a tag owned by the user, Then findUserTagByName finds it in another case outside ASCII`() {
        // Given
        val user = createAndSaveUser()
        val tag = saveTag("élodie", user)

        // When
        val found = repository.findUserTagByName(user = user, name = tagName("ÉLODIE"))

        // Then
        assertEquals(tag.id, found?.id)
    }

    @Test
    fun `Given a tag name already held in another case outside ASCII, Then saveTag is refused by the store`() {
        // Given: no translation, deliberately. TagCreator.findOrCreate reads through the same fold
        // first, so this violation is unreachable from the API and stays a PersistenceException.
        val user = createAndSaveUser()
        saveTag("élodie", user)

        // When / Then
        assertThrows<PersistenceException> { saveTag("ÉLODIE", user) }
    }

    @Test
    fun `Given two authors, Then each may hold the same tag name`() {
        // Given
        val first = createAndSaveUser()
        val second = createAndSaveUser()
        saveTag("landscape", first)

        // When
        val tag = saveTag("landscape", second)

        // Then
        assertEquals("landscape", tag.name.text)
    }

    // --- Matching ---

    private fun matching(user: User, query: String, limit: Int = 10) =
        repository.findTagsForUserMatching(user = user, query = query, limit = limit)

    @Test
    fun `Given a name beginning with the term and one merely holding it, Then the first comes first`() {
        // Given
        val user = createAndSaveUser()
        val contained = saveTag("landscape", user)
        val prefixed = saveTag("scapegoat", user)

        // When
        val found = matching(user, "scape")

        // Then: what you are typing the start of is the suggestion you meant
        assertEquals(listOf(prefixed.id, contained.id), found.map { it.id })
    }

    @Test
    fun `Given a name matching neither, Then findTagsForUserMatching leaves it out`() {
        // Given
        val user = createAndSaveUser()
        val cat = saveTag("cat", user)
        saveTag("dog", user)

        // When
        val found = matching(user, "cat")

        // Then
        assertEquals(listOf(cat.id), found.map { it.id })
    }

    @Test
    fun `Given a tag stored élodie, Then the term ÉLO finds it`() {
        // Given: LIKE folds under the column's collation, as the unique index does
        val user = createAndSaveUser()
        val tag = saveTag("élodie", user)

        // When
        val found = matching(user, "ÉLO")

        // Then
        assertEquals(listOf(tag.id), found.map { it.id })
    }

    @Test
    fun `Given a tag stored Cafe accented, Then the accented term finds it and the bare one does not`() {
        // Given
        val user = createAndSaveUser()
        val tag = saveTag("Café", user)

        // When
        val accented = matching(user, "café")
        val bare = matching(user, "cafe")

        // Then: no accent folding, and this assertion is what says so the day someone adds it
        assertEquals(listOf(tag.id), accented.map { it.id })
        assertTrue(bare.isEmpty(), "$bare")
    }

    @Test
    fun `Given more prefix matches than the limit, Then the contains query is not asked`() {
        // Given
        val user = createAndSaveUser()
        saveTag("cat", user)
        saveTag("catalogue", user)
        saveTag("bobcat", user)

        // When
        val found = matching(user, "cat", limit = 2)

        // Then: the limit is the caller's, and it is spent before the second query is reached
        assertEquals(2, found.size)
        assertTrue(found.none { it.name.text == "bobcat" }, "$found")
    }

    @Test
    fun `Given a name matching both halves, Then it is served once`() {
        // Given
        val user = createAndSaveUser()
        saveTag("cat", user)
        saveTag("bobcat", user)

        // When
        val found = matching(user, "cat")

        // Then
        assertEquals(listOf("cat", "bobcat"), found.map { it.name.text })
    }

    @Test
    fun `Given a limit of zero, Then findTagsForUserMatching serves nothing`() {
        // Given: Ebean's setMaxRows reads zero as unbounded, so an unguarded limit would serve the lot
        val user = createAndSaveUser()
        saveTag("cat", user)
        saveTag("bobcat", user)

        // When
        val found = matching(user, "cat", limit = 0)

        // Then
        assertTrue(found.isEmpty(), "$found")
    }

    @Test
    fun `Given another author's matching tag, Then findTagsForUserMatching leaves it out`() {
        // Given
        val user = createAndSaveUser()
        val other = createAndSaveUser()
        val own = saveTag("cat", user)
        saveTag("cat", other)

        // When
        val found = matching(user, "cat")

        // Then
        assertEquals(listOf(own.id), found.map { it.id })
    }

    // --- Creation timestamps ---

    @Test
    fun `Given a saved tag, Then reading it back exposes its creation timestamp`() {
        // Given
        val user = createAndSaveUser()
        repository.saveTag(
            Tag(id = randomUUID(), author = user, name = tagName("landscape"), createdAt = storableNow())
        )

        // When
        val found = repository.findUserTagByName(user = user, name = tagName("landscape"))

        // Then
        assertNotNull(found?.createdAt)
    }
}
