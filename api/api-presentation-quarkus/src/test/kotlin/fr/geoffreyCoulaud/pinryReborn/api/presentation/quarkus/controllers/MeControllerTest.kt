package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.usecases.AccountDeleter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PasswordChanger
import io.mockk.every
import io.mockk.mockk
import io.quarkus.security.identity.SecurityIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MeControllerTest {
    @Test
    fun `Given an authenticated caller, Then getCurrentUser returns their id and name`() {
        val user = aUser(name = "alice")
        val identity = mockk<SecurityIdentity> { every { getAttribute<User>("user") } returns user }
        val passwordChanger = mockk<PasswordChanger>()
        val accountDeleter = mockk<AccountDeleter>()
        val dto = MeController(identity, passwordChanger, accountDeleter).getCurrentUser()
        assertEquals(user.id, dto.id)
        assertEquals("alice", dto.name)
    }
}
