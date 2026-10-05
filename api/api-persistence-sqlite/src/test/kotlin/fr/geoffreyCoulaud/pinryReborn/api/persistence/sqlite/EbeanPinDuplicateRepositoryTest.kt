package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.withActivePins
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanPinDuplicateRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private val pins = PinRepository(persistor)

    // Active pins of one user, the last [recycled] of them in the recycle bin.
    private fun storedPins(count: Int, recycled: Int = 0): List<Pin> {
        val user =
            UserRepository(persistor).saveUser(User(randomUUID(), createRandomString(), createdAt = storableNow()))
        val stored = List(count) {
            pins.savePin(
                Pin(
                    randomUUID(), user, null, null, "", emptyList(), emptyList(),
                    createdAt = storableNow(), updatedAt = storableNow(),
                ),
            )
        }
        stored.takeLast(recycled).forEach { pins.softDeletePin(it, storableNow()) }
        return stored
    }

    @Test
    fun `Given pending, rejected and hidden pairs, Then the pins asked that hold a shown pending pair are found`() {
        // Given: the pending pair's second pin is not asked; a recycled or a gone pin hides a pair
        val (pending, unasked) = storedPins(2).map { it.id }
        val (rejectedFirst, rejectedSecond) = storedPins(2).map { it.id }
        val (active, recycled) = storedPins(2, recycled = 1).map { it.id }
        repository.addMissing(pending, listOf(unasked))
        repository.addMissing(rejectedFirst, listOf(rejectedSecond))
        reject(rejectedFirst, rejectedSecond)
        repository.addMissing(active, listOf(recycled, randomUUID()))

        // When
        val found = repository.findPinIdsWithPending(listOf(pending, rejectedFirst, rejectedSecond, active, recycled))

        // Then
        assertEquals(setOf(pending), found)
    }

    @Test
    fun `Given a pin's pairs with active, recycled and gone pins, Then only the pairs of two active pins are shown`() {
        // Given
        val (pin, pending, rejected) = storedPins(3).map { it.id }
        val (recycled) = storedPins(1, recycled = 1).map { it.id }
        repository.addMissing(pin, listOf(pending, rejected, recycled, randomUUID()))
        reject(pin, rejected)

        // When: one pair read from both sides, so each pin is the lower id once
        val shown = repository.findShownFor(pin)
        val shownFromTheOtherSide = repository.findShownFor(pending)

        // Then
        assertEquals(mapOf(pending to false, rejected to true), shown)
        assertEquals(mapOf(pin to false), shownFromTheOtherSide)
        assertEquals(emptyMap<UUID, Boolean>(), repository.findShownFor(recycled))
    }

    @Test
    fun `Given a pin's shown pairs as Ebean builds them, Then its plan finds each pin by key and lists no pin`() {
        // Given
        val pin = randomUUID()
        val query = QPinDuplicateModel().firstPinId.equalTo(pin).withActivePins()
        query.findList()
        val explain = database.sqlQuery("explain query plan ${query.query().generatedSql}").setParameter(1, pin)

        // When
        val plan = explain.findList().map { "${it["detail"]}" }

        // Then: each pin by its primary key, rather than a list of every active pin
        assertEquals(2, plan.count { it.startsWith("SEARCH") && it.contains("(id=?)") }, "$plan")
        assertFalse(plan.any { it.contains("LIST SUBQUERY") || it.startsWith("SCAN") }, "$plan")
    }

    @Test
    fun `Given a shown pair and a hidden one, Then a rejection and a restoration reach the shown one alone`() {
        // Given
        val (pin, other, recycled) = storedPins(3, recycled = 1).map { it.id }
        repository.addMissing(pin, listOf(other, recycled))

        // When: rejected from one side, restored from the other
        val rejected = repository.setRejected(pin, other, storableNow())
        val afterTheRejection = pairs()
        val restored = repository.setRejected(other, pin, rejectedAt = null)
        val hidden = repository.setRejected(pin, recycled, storableNow())
        val absent = repository.setRejected(pin, randomUUID(), storableNow())

        // Then
        assertEquals(listOf(true, true, false, false), listOf(rejected, restored, hidden, absent))
        assertTrue(setOf(pin, other) to true in afterTheRejection)
        assertEquals(setOf(setOf(pin, other) to false, setOf(pin, recycled) to false), pairs())
    }

    @Test
    fun `Given pairs of stored, recycled and gone pins, Then the orphan sweep deletes those naming a gone pin`() {
        // Given: a gone pin on either side, since its id sorts first or second
        val (stored, recycled) = storedPins(2, recycled = 1)
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
