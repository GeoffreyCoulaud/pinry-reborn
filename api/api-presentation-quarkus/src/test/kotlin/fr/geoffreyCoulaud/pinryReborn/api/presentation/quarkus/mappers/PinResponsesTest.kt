package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicate
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class PinResponsesTest {
    private val user = User(randomUUID(), createRandomString(), createdAt = TestTime.now)
    private val pins = List(3) {
        Pin(randomUUID(), user, null, null, "", emptyList(), emptyList(), TestTime.now, TestTime.now)
    }

    // The second pin holds a pending duplicate.
    private val duplicates = CountingDuplicates(pending = setOf(pins[1].id))
    private val resolvePinMediaState = mockk<ResolvePinMediaState>().also {
        every { it.statesFor(any()) } returns emptyMap()
    }
    private val responses = PinResponses(resolvePinMediaState, PinDuplicates(duplicates, mockk(), mockk()))

    @Test
    fun `Given a page of three pins, Then their duplicates flags cost one repository call`() {
        // When
        val page = responses.page(Page(items = pins, previousCursor = null, nextCursor = null))

        // Then
        assertEquals(listOf(false, true, false), page.pins.map { it.hasPendingDuplicates })
        assertEquals(1, duplicates.reads)
    }

    @Test
    fun `Given a pin's duplicates, Then each carries its pin's flag and its rejection, for one repository call`() {
        // When
        val list = responses.duplicates(listOf(PinDuplicate(pins[1], false), PinDuplicate(pins[2], true)))

        // Then
        val flagsAndRejections = list.duplicates.map { it.pin.hasPendingDuplicates to it.rejected }
        assertEquals(listOf(true to false, false to true), flagsAndRejections)
        assertEquals(1, duplicates.reads)
    }

    private class CountingDuplicates(private val pending: Set<UUID>) : PinDuplicateRepositoryInterface {
        var reads = 0

        override fun findPinIdsWithPending(pinIds: Collection<UUID>): Set<UUID> {
            reads++
            return pending intersect pinIds.toSet()
        }

        override fun deletePending(pinId: UUID) = error("not used")

        override fun addMissing(pinId: UUID, otherPinIds: Collection<UUID>) = error("not used")

        override fun deleteOrphans(): Int = error("not used")

        override fun findShownFor(pinId: UUID): Map<UUID, Boolean> = error("not used")

        override fun setRejected(pinId: UUID, otherPinId: UUID, rejectedAt: Instant) = error("not used")
    }
}
