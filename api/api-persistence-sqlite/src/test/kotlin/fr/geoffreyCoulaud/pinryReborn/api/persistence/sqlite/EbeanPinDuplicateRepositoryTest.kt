package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanPinDuplicateRepository
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
    fun `Given a pin's pending and rejected pairs, Then deleting its pending pairs keeps the rejected and the others'`() {
        // Given
        val (pin, pending, rejected, other) = List(4) { randomUUID() }
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
        val (pin, heldFirst, heldRejected, added) = List(4) { randomUUID() }
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
