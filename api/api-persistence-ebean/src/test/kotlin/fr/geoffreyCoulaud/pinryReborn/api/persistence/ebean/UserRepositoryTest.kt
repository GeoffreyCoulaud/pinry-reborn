package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.username
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.users.UsernameAlreadyTakenException
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.UserModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import jakarta.persistence.PersistenceException
import java.time.temporal.ChronoUnit
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

class UserRepositoryTest : RepositoryTest() {
    private val repository = UserRepository(persistor)

    private fun saveUser(user: User = aUser()) = repository.saveUser(user)

    @Test
    fun `saveUser should persist user and return it with same id`() {
        // Given
        val user = aUser()

        // When
        val savedUser = repository.saveUser(user)

        // Then
        assertEquals(user.id, savedUser.id)
        assertEquals(user.name, savedUser.name)
    }

    @Test
    fun `findUser should return user when exists`() {
        // Given
        val user = aUser()
        repository.saveUser(user)

        // When
        val foundUser = repository.findUserById(user.id)

        // Then
        assertNotNull(foundUser)
        assertEquals(user.id, foundUser!!.id)
        assertEquals(user.name, foundUser.name)
    }

    @Test
    fun `findUser should return null when user does not exist`() {
        // When
        val foundUser = repository.findUserById(randomUUID())

        // Then
        assertNull(foundUser)
    }

    @Test
    fun `Given a tombstoned user, Then normal lookups hide it but including-deleted finds it`() {
        // Given
        val user = saveUser()
        repository.markPendingDeletion(user, storableNow())

        // When / Then
        assertNull(repository.findUserById(user.id))
        assertNull(repository.findUserByName(user.name.text))
        val found = repository.findUserByIdIncludingDeleted(user.id)
        assertEquals(user.id, found?.id)
        assertNotNull(found?.softDeletedAt)
    }

    @Test
    fun `Given an active user, Then markPendingDeletion stamps the instant it was given`() {
        // Given
        val user = saveUser()
        val tombstonedAt = storableNow()

        // When
        repository.markPendingDeletion(user, tombstonedAt)

        // Then
        assertEquals(tombstonedAt, repository.findUserByIdIncludingDeleted(user.id)?.softDeletedAt)
    }

    @Test
    fun `Given an already tombstoned user, Then a second markPendingDeletion keeps the first instant`() {
        // Given: re-stamping would push the retention cutoff forward on every repeated request
        val user = saveUser()
        val firstRequest = storableNow()
        val secondRequest = firstRequest.plus(1, ChronoUnit.HOURS)
        repository.markPendingDeletion(user, firstRequest)

        // When
        repository.markPendingDeletion(user, secondRequest)

        // Then
        assertEquals(firstRequest, repository.findUserByIdIncludingDeleted(user.id)?.softDeletedAt)
    }

    @Test
    fun `Given a tombstoned user read back, Then saving it again keeps it tombstoned`() {
        // Given
        val user = saveUser()
        val tombstonedAt = storableNow()
        repository.markPendingDeletion(user, tombstonedAt)
        val tombstoned = requireNotNull(repository.findUserByIdIncludingDeleted(user.id))

        // When
        repository.saveUser(tombstoned)

        // Then
        assertEquals(tombstonedAt, repository.findUserByIdIncludingDeleted(user.id)?.softDeletedAt)
        assertNull(repository.findUserById(user.id))
    }

    @Test
    fun `Given a tombstoned user, Then permanentlyDeleteUser removes it entirely`() {
        // Given
        val user = saveUser()
        repository.markPendingDeletion(user, storableNow())

        // When
        repository.permanentlyDeleteUser(user)

        // Then
        assertNull(repository.findUserByIdIncludingDeleted(user.id))
    }

    @Test
    fun `Given an active user, Then findUserById returns it with no recycling instant`() {
        // Given
        val user = saveUser()

        // When / Then
        assertNull(repository.findUserById(user.id)?.softDeletedAt)
    }

    @Test
    fun `Given a never-saved user, Then markPendingDeletion is a no-op`() {
        // Given
        val user = aUser()

        // When / Then
        repository.markPendingDeletion(user, storableNow())
    }

    @Test
    fun `Given a never-saved user, Then permanentlyDeleteUser is a no-op`() {
        // Given
        val user = aUser()

        // When / Then
        repository.permanentlyDeleteUser(user)
    }

    @Test
    fun `saveUser should update existing user`() {
        // Given
        val originalUser = aUser(name = "original-name")
        repository.saveUser(originalUser)

        // When
        val updatedUser = originalUser.copy(name = username("updated-name"))
        repository.saveUser(updatedUser)

        // Then
        val foundUser = repository.findUserById(originalUser.id)
        assertNotNull(foundUser)
        assertEquals("updated-name", foundUser!!.name.text)
    }

    @Test
    fun `Given a saved user, Then findUserByName finds it in another case`() {
        // Given: an account name is ASCII; the collation's fold outside it is pinned on tags and boards
        val user = aUser(name = "Alice")
        repository.saveUser(user)

        // When
        val foundUser = repository.findUserByName("ALICE")

        // Then
        assertEquals("Alice", foundUser?.name?.text)
    }

    @Test
    fun `Given a name taken in another case, Then saveUser throws UsernameAlreadyTakenException`() {
        // Given: the column's collation folds case, so the store sees these two names as one
        val takenName = "Alice${createRandomString()}"
        saveUser(aUser(name = takenName))

        // When, Then
        assertThrows<UsernameAlreadyTakenException> { saveUser(aUser(name = takenName.uppercase())) }
    }

    @Test
    fun `Given a stored account name the factory refuses, Then reading it throws rather than invent a name`() {
        // Given: a row no write of the API makes
        val id = randomUUID()
        database
            .sqlUpdate("insert into users (id, name, created_at) values (?, 'a b', ?)")
            .setParameter(1, id)
            .setParameter(2, storableNow())
            .execute()

        // When / Then
        assertThrows<IllegalStateException> { repository.findUserById(id) }
    }

    @Test
    fun `Given a name held by a tombstoned account, Then saveUser throws UsernameAlreadyTakenException`() {
        // Given: ux_users_name covers every row, so a pending deletion does not release the name
        val tombstoned = saveUser()
        repository.markPendingDeletion(tombstoned, storableNow())

        // When, Then
        assertThrows<UsernameAlreadyTakenException> { saveUser(aUser(name = tombstoned.name.text)) }
    }

    @Test
    fun `Given a non-unique persistence failure, Then saveUser propagates it untouched`() {
        // Given: a NOT NULL violation, in the same SQLState class 23 as the unique one
        val violation = notNullConstraintViolation()
        val failingRepository = UserRepository(NotNullViolatingPersistor(persistor, violation))
        val user = aUser()

        // When, Then: translating this one would report a broken column as a taken name
        val thrown = assertThrows<PersistenceException> { failingRepository.saveUser(user) }
        assertSame(violation, thrown)
    }

    /** The shape of a failure that must never be translated: a constraint violation of another SQLState. */
    private fun notNullConstraintViolation() =
        PersistenceException(
            "null value violates not-null constraint",
            PSQLException("null value violates not-null constraint", PSQLState.NOT_NULL_VIOLATION),
        )

    /** Raises [violation] on a user merge, which is the call saveUser's translation wraps. */
    private class NotNullViolatingPersistor(
        private val delegate: Persistor,
        private val violation: PersistenceException,
    ) : Persistor by delegate {
        override fun merge(bean: Any) {
            if (bean is UserModel) throw violation
            delegate.merge(bean)
        }
    }

    @Test
    fun `Given no user with the given name, Then findUserByName returns null`() {
        // Given
        // When
        val foundUser = repository.findUserByName("nobody")

        // Then
        assertNull(foundUser)
    }

    // --- Creation timestamps ---

    @Test
    fun `Given a saved user, Then reading it back exposes its creation timestamp`() {
        // Given
        val user = saveUser()

        // When
        val found = repository.findUserById(user.id)

        // Then
        assertNotNull(found?.createdAt)
    }

    // --- Tombstone sweep ---

    @Test
    fun `Given tombstones and active users, Then findTombstonedUsersSoftDeletedBefore returns only stale tombstones`() {
        // Given
        val cutoff = storableNow()
        saveUser()
        val staleTombstone = saveUser()
        val boundaryTombstone = saveUser()
        val freshTombstone = saveUser()
        repository.markPendingDeletion(staleTombstone, cutoff.minus(2, ChronoUnit.HOURS))
        repository.markPendingDeletion(boundaryTombstone, cutoff)
        repository.markPendingDeletion(freshTombstone, cutoff.plus(2, ChronoUnit.HOURS))

        // When
        val tombstones = repository.findTombstonedUsersSoftDeletedBefore(cutoff)

        // Then: only the stale tombstone is returned; the active user, the tombstone stamped on the
        // cutoff and the fresh one are not
        assertEquals(1, tombstones.size)
        val only = tombstones.single()
        assertEquals(staleTombstone.id, only.id)
        assertNotNull(only.softDeletedAt)
    }
}
