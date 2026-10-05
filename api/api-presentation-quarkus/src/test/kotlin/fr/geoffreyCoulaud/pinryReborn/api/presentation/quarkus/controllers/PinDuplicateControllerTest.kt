package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinDuplicateUpdateInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicate
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
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
    private val controller = PinDuplicateController(
        pinDuplicates = pinDuplicates,
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
}
