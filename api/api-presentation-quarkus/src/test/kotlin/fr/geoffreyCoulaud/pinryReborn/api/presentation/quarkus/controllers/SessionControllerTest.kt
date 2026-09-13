package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.IssuedSession
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.SessionToken
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.security.SessionExpiryPolicy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.SessionTransportDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.SessionCreationInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.CreatedSessionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ExistingSessionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.SessionCookie
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SessionCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SessionRenewer
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SessionRevoker
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.UserAuthenticationInvalidPasswordError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.quarkus.security.AuthenticationFailedException
import io.quarkus.security.identity.SecurityIdentity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID

class SessionControllerTest {
    private val creator = mockk<SessionCreator>()
    private val renewer = mockk<SessionRenewer>(relaxed = true)
    private val revoker = mockk<SessionRevoker>(relaxed = true)
    private val policy = SessionExpiryPolicy(Duration.ofDays(30), Duration.ofHours(12), 0.75)
    private val identity = mockk<SecurityIdentity>()
    private val controller = SessionController(creator, renewer, revoker, policy, identity)

    private val user = User(randomUUID(), "alice", createdAt = TestTime.now)
    private val issued = IssuedSession(
        "tok",
        Instant.parse("2026-08-01T00:00:00Z"),
        Instant.parse("2026-07-25T00:00:00Z"),
    )

    private fun credentials(
        transport: SessionTransportDto = SessionTransportDto.BEARER,
        rememberMe: Boolean? = null,
    ) = SessionCreationInputDto("alice", "pw", transport = transport, rememberMe = rememberMe)

    private fun currentSession(persistent: Boolean = false, transport: SessionTransportDto) =
        SessionToken(randomUUID(), user, TestTime.now, persistent = persistent, createdAt = TestTime.now)
            .also {
                every { identity.getAttribute<SessionToken>("sessionToken") } returns it
                every { identity.getAttribute<SessionTransportDto>("sessionTransport") } returns transport
            }

    @Test
    fun `Given rememberMe true, Then createSession passes persistent=true and returns the created dto`() {
        every { creator.create(name = "alice", password = "pw", persistent = true) } returns issued
        val response = controller.createSession(credentials(rememberMe = true))
        assertEquals("tok", (response.entity as CreatedSessionOutputDto).token)
        assertEquals("no-store", response.getHeaderString("Cache-Control"))
        verify { creator.create(name = "alice", password = "pw", persistent = true) }
    }

    @Test
    fun `Given rememberMe absent (null), Then createSession defaults persistent to false`() {
        every { creator.create(name = "alice", password = "pw", persistent = false) } returns issued
        controller.createSession(credentials())
        verify { creator.create(name = "alice", password = "pw", persistent = false) }
    }

    @Test
    fun `Given invalid credentials, Then createSession raises AuthenticationFailedException`() {
        every { creator.create(any(), any(), any()) } throws UserAuthenticationInvalidPasswordError()
        assertThrows<AuthenticationFailedException> { controller.createSession(credentials()) }
    }

    @Test
    fun `Given the bearer transport, Then createSession answers 201 with the token and no cookie`() {
        // Given
        every { creator.create(any(), any(), any()) } returns issued

        // When
        val response = controller.createSession(credentials(SessionTransportDto.BEARER))

        // Then
        assertEquals(201, response.status)
        assertEquals("tok", (response.entity as CreatedSessionOutputDto).token)
        assertNull(response.cookies[SessionCookie.NAME])
    }

    @Test
    fun `Given the cookie transport, Then createSession answers 200 with the cookie and no token`() {
        // Given
        every { creator.create(any(), any(), any()) } returns issued

        // When
        val response = controller.createSession(credentials(SessionTransportDto.COOKIE, rememberMe = true))

        // Then
        assertEquals(200, response.status)
        val body = response.entity as ExistingSessionOutputDto
        assertEquals(issued.expiresAt, body.expiresAt)
        assertEquals(issued.renewAfter, body.renewAfter)
        assertEquals(true, body.persistent)
        assertEquals("tok", response.cookies[SessionCookie.NAME]?.value)
    }

    @Test
    fun `Given a current session, Then getCurrentSession returns its metadata without a token`() {
        val current = SessionToken(
            randomUUID(),
            user,
            Instant.parse("2026-08-01T00:00:00Z"),
            persistent = true,
            createdAt = TestTime.now,
        )
        every { identity.getAttribute<SessionToken>("sessionToken") } returns current
        val dto = controller.getCurrentSession()
        assertEquals(current.expiresAt, dto.expiresAt)
        assertEquals(policy.renewAfterFor(current.expiresAt, true), dto.renewAfter)
        assertEquals(true, dto.persistent)
    }

    @Test
    fun `Given a bearer session, Then renewSession answers 201 with the new token and no cookie`() {
        // Given
        val current = currentSession(transport = SessionTransportDto.BEARER)
        every { renewer.renew(current) } returns issued

        // When
        val response = controller.renewSession()

        // Then
        assertEquals(201, response.status)
        assertEquals("tok", (response.entity as CreatedSessionOutputDto).token)
        assertEquals("no-store", response.getHeaderString("Cache-Control"))
        assertNull(response.cookies[SessionCookie.NAME])
    }

    @Test
    fun `Given a cookie session, Then renewSession answers 200 with a fresh cookie and no token`() {
        // Given
        val current = currentSession(persistent = true, transport = SessionTransportDto.COOKIE)
        every { renewer.renew(current) } returns issued

        // When
        val response = controller.renewSession()

        // Then
        assertEquals(200, response.status)
        assertEquals(true, (response.entity as ExistingSessionOutputDto).persistent)
        assertEquals("tok", response.cookies[SessionCookie.NAME]?.value)
    }

    @Test
    fun `Given a current session, Then revokeCurrentSession deletes the current token`() {
        val current = currentSession(transport = SessionTransportDto.BEARER)
        val response = controller.revokeCurrentSession()
        assertEquals(204, response.status)
        assertNull(response.cookies[SessionCookie.NAME])
        verify { revoker.revokeCurrent(current) }
    }

    @Test
    fun `Given a cookie session, Then revokeCurrentSession clears the cookie`() {
        // Given
        currentSession(transport = SessionTransportDto.COOKIE)

        // When
        val response = controller.revokeCurrentSession()

        // Then
        assertEquals(0, response.cookies[SessionCookie.NAME]?.maxAge)
    }

    @Test
    fun `Given the caller, Then revokeAllSessions deletes all their tokens`() {
        every { identity.getAttribute<User>("user") } returns user
        every { identity.getAttribute<SessionTransportDto>("sessionTransport") } returns SessionTransportDto.BEARER
        val response = controller.revokeAllSessions()
        assertEquals(204, response.status)
        verify { revoker.revokeAll(user) }
    }

    @Test
    fun `Given a cookie session, Then revokeAllSessions clears the cookie`() {
        // Given
        every { identity.getAttribute<User>("user") } returns user
        every { identity.getAttribute<SessionTransportDto>("sessionTransport") } returns SessionTransportDto.COOKIE

        // When
        val response = controller.revokeAllSessions()

        // Then
        assertEquals(0, response.cookies[SessionCookie.NAME]?.maxAge)
    }
}
