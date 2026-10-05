package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanPinDuplicateRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.UUID.randomUUID

class EbeanPinDuplicateRepositoryTest : RepositoryTest() {
    private val repository = EbeanPinDuplicateRepository(persistor)

    // Each pair as its two pin ids, and whether the user rejected it.
    private fun pairs(): Set<Pair<Set<UUID>, Boolean>> =
        QPinDuplicateModel().findList().map { setOf(it.firstPinId, it.secondPinId) to (it.rejectedAt != null) }.toSet()

    private fun reject(first: UUID, second: UUID) {
        QPinDuplicateModel().firstPinId.isIn(first, second).secondPinId.isIn(first, second)
            .asUpdate().set("rejectedAt", storableNow()).update()
    }

    @Test
    fun `Given pairs of stored, recycled and gone pins, Then the orphan sweep deletes those naming a gone pin`() {
        // Given: a gone pin on either side, since its id sorts first or second
        val pins = PinRepository(persistor)
        val user =
            UserRepository(persistor).saveUser(User(randomUUID(), createRandomString(), createdAt = storableNow()))
        val (stored, recycled) = List(2) {
            pins.savePin(
                Pin(
                    randomUUID(), user, null, null, "", emptyList(), emptyList(),
                    createdAt = storableNow(), updatedAt = storableNow(),
                ),
            )
        }
        pins.softDeletePin(recycled, storableNow())
        val (goneLow, goneHigh) = listOf("00000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff")
            .map(UUID::fromString)
        repository.addMissing(stored.id, listOf(recycled.id, goneLow, goneHigh))

        // When
        val deleted = repository.deleteOrphans()

        // Then
        assertEquals(2, deleted)
        assertEquals(setOf(setOf(stored.id, recycled.id) to false), pairs())
    }

    @Test
    fun `Given a pin's pending and rejected pairs, Then deleting the pending keeps the rejected and others'`() {
        // Given
        val (pin, pending, rejected) = List(3) { randomUUID() }
        val other = randomUUID()
        repository.addMissing(pin, listOf(pending, rejected))
        repository.addMissing(other, listOf(pending))
        reject(pin, rejected)

        // When
        repository.deletePending(pin)

        // Then
        assertEquals(setOf(setOf(pin, rejected) to true, setOf(other, pending) to false), pairs())
    }

    @Test
    fun `Given pairs already held from either side, Then only the missing ones are added and a rejection stays`() {
        // Given: the held pairs name the pin first and second, whichever order its id sorts in
        val (heldFirst, heldRejected, added) = List(3) { randomUUID() }
        val pin = randomUUID()
        repository.addMissing(heldFirst, listOf(pin))
        repository.addMissing(pin, listOf(heldRejected))
        reject(pin, heldRejected)

        // When
        repository.addMissing(pin, listOf(heldFirst, heldRejected, added))

        // Then
        assertEquals(
            setOf(setOf(pin, heldFirst) to false, setOf(pin, heldRejected) to true, setOf(pin, added) to false),
            pairs(),
        )
        assertTrue(QPinDuplicateModel().findList().all { it.firstPinId.toString() < it.secondPinId.toString() })
    }
}
