package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinDuplicateUpdateInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinMergeInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicate
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMerger
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.quarkus.security.identity.SecurityIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class PinDuplicateControllerTest {
    private val user = User(randomUUID(), createRandomString(), createdAt = TestTime.now)
    private val securityIdentity = mockk<SecurityIdentity>().also {
        every { it.getAttribute<User>("user") } returns user
    }
    private val pinDuplicates = mockk<PinDuplicates>().also { every { it.pendingAmong(any()) } returns emptySet() }

    // The real assembler over stubbed resolvers: the responses under assertion are the mapped ones.
    private val resolvePinMediaState = mockk<ResolvePinMediaState>().also {
        every { it.statesFor(any()) } returns emptyMap()
    }
    private val pinMerger = mockk<PinMerger>()
    private val controller = PinDuplicateController(
        pinDuplicates = pinDuplicates,
        pinMerger = pinMerger,
        securityIdentity = securityIdentity,
        pinResponses = PinResponses(resolvePinMediaState, pinDuplicates),
    )

    private fun pin() = Pin(randomUUID(), user, null, null, "", emptyList(), emptyList(), TestTime.now, TestTime.now)

    @Test
    fun `Given a pin's duplicates, Then each is answered as its pin and its rejection`() {
        // Given
        val (pin, pending, rejected) = List(3) { pin() }
        every { pinDuplicates.list(pin.id, user) } returns
            listOf(PinDuplicate(pending, rejected = false), PinDuplicate(rejected, rejected = true))

        // When
        val answered = controller.listDuplicates(pin.id).entity.duplicates

        // Then
        assertEquals(listOf(pending.id to false, rejected.id to true), answered.map { it.pin.id to it.rejected })
    }

    @Test
    fun `Given a rejection, Then the duplicate is answered rejected`() {
        // Given
        val (pin, other) = List(2) { pin() }
        every { pinDuplicates.setRejected(pin.id, other.id, true, user) } returns PinDuplicate(other, rejected = true)

        // When
        val answered = controller.updateDuplicate(pin.id, other.id, PinDuplicateUpdateInputDto(rejected = true)).entity

        // Then
        assertEquals(other.id to true, answered.pin.id to answered.rejected)
    }

    @Test
    fun `Given a merge, Then the kept pin is answered`() {
        // Given
        val (kept, absorbed) = List(2) { pin() }
        every { pinMerger.merge(kept.id, listOf(absorbed.id), user) } returns kept

        // When
        val answered = controller.mergePins(PinMergeInputDto(kept.id, listOf(absorbed.id))).entity

        // Then
        assertEquals(kept.id, answered.id)
    }

    @Test
    fun `Given a merge body, Then it is valid only when it names each pin once`() {
        // Given
        val (kept, absorbed) = List(2) { randomUUID() }

        // When
        val validity = listOf(listOf(absorbed), listOf(absorbed, absorbed), listOf(kept, absorbed))
            .map { PinMergeInputDto(kept, it).isEachPinNamedOnce }

        // Then
        assertEquals(listOf(true, false, false), validity)
    }
}
