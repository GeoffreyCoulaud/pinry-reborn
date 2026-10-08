package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPersonModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinCreatorModel
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A pin's publisher, creators and publication instant, saved and read by `PinRepository`. */
class PinRepositoryPeopleTest : PinRepositoryFixtures() {
    private fun savePinWithCreators(user: User, vararg creators: Person): Pin =
        repository.savePin(createPin().copy(author = user, creators = creators.toList()))

    private fun creatorRowsOf(pin: Pin): Int = QPinCreatorModel().pin.id.equalTo(pin.id).findCount()

    private fun assertHoldsPeople(expected: Pin, actual: Pin?) {
        assertEquals(expected.publisher, actual?.publisher)
        assertEquals(expected.creators.toSet(), actual?.creators?.toSet())
        assertEquals(expected.publishedAt, actual?.publishedAt)
    }

    @Test
    fun `Given a pin saved with a publisher, two creators and a date, Then findPinById reads them back equal`() {
        // Given
        val user = createAndSaveUser()
        val pin =
            createPin()
                .copy(
                    author = user,
                    publisher = createAndSavePerson(user),
                    creators = listOf(createAndSavePerson(user), createAndSavePerson(user)),
                    publishedAt = PUBLISHED_AT,
                )
        repository.savePin(pin)

        // When
        val found = repository.findPinById(pin.id)

        // Then
        assertHoldsPeople(pin, found)
    }

    @Test
    fun `Given a pin saved with a publisher, two creators and a date, Then findPinsByIds reads them back equal`() {
        // Given
        val user = createAndSaveUser()
        val pin =
            createPin()
                .copy(
                    author = user,
                    publisher = createAndSavePerson(user),
                    creators = listOf(createAndSavePerson(user), createAndSavePerson(user)),
                    publishedAt = PUBLISHED_AT,
                )
        repository.savePin(pin)

        // When
        val found = repository.findPinsByIds(listOf(pin.id)).single()

        // Then
        assertHoldsPeople(pin, found)
    }

    @Test
    fun `Given a pin with two creators saved again with one, Then no row is left for the creator removed`() {
        // Given
        val user = createAndSaveUser()
        val kept = createAndSavePerson(user)
        val removed = createAndSavePerson(user)
        val pin = savePinWithCreators(user, kept, removed)

        // When
        repository.savePin(pin.copy(creators = listOf(kept)))

        // Then
        assertEquals(0, QPinCreatorModel().pin.id.equalTo(pin.id).person.id.equalTo(removed.id).findCount())
        assertEquals(listOf(kept), repository.findPinById(pin.id)?.creators)
    }

    @Test
    fun `Given a creator named twice, Then savePin writes its row once`() {
        // Given
        val user = createAndSaveUser()
        val creator = createAndSavePerson(user)

        // When
        val pin = savePinWithCreators(user, creator, creator)

        // Then
        assertEquals(1, creatorRowsOf(pin))
    }

    @Test
    fun `Given a recycled pin with a creator, Then permanentlyDeletePin leaves no creator row and keeps the person`() {
        // Given
        val user = createAndSaveUser()
        val creator = createAndSavePerson(user)
        val pin = repository.softDeletePin(savePinWithCreators(user, creator), storableNow())

        // When
        repository.permanentlyDeletePin(pin)

        // Then
        assertEquals(0, creatorRowsOf(pin))
        assertEquals(1, QPersonModel().id.equalTo(creator.id).findCount())
    }

    @Test
    fun `Given a recycled pin with a creator, Then emptying the bin leaves no creator row`() {
        // Given
        val user = createAndSaveUser()
        val pin = repository.softDeletePin(savePinWithCreators(user, createAndSavePerson(user)), storableNow())

        // When
        repository.permanentlyDeleteAllSoftDeletedPinsForUser(user)

        // Then
        assertEquals(0, creatorRowsOf(pin))
    }

    @Test
    fun `Given an active pin with a creator, Then permanentlyDeleteAllPinsForUser leaves no creator row`() {
        // Given
        val user = createAndSaveUser()
        val pin = savePinWithCreators(user, createAndSavePerson(user))

        // When
        repository.permanentlyDeleteAllPinsForUser(user)

        // Then
        assertEquals(0, creatorRowsOf(pin))
    }

    private companion object {
        val PUBLISHED_AT: Instant = Instant.parse("2019-05-01T12:34:56Z")
    }
}
