package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.SessionTransportDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.TokenAuthenticationRequest
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.core.http.Cookie
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.RoutingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookieAuthenticationMechanismTest {
    private val mechanism = CookieAuthenticationMechanism()
    private val idpManager = mockk<IdentityProviderManager>()

    private fun contextWithCookie(value: String?): RoutingContext {
        val cookie = value?.let { mockk<Cookie> { every { getValue() } returns it } }
        val request = mockk<HttpServerRequest> { every { getCookie(SessionCookie.NAME) } returns cookie }
        return mockk { every { request() } returns request }
    }

    @Test
    fun `Given the session cookie, Then it authenticates the token it carries`() {
        // Given
        val identity = mockk<SecurityIdentity>()
        val captured = slot<TokenAuthenticationRequest>()
        every { idpManager.authenticate(capture(captured)) } returns Uni.createFrom().item(identity)

        // When
        val result = mechanism.authenticate(contextWithCookie("abc.def"), idpManager).await().indefinitely()

        // Then
        assertEquals(identity, result)
        assertEquals("abc.def", captured.captured.token.token)
        assertEquals(SessionTransportDto.COOKIE.credentialType, captured.captured.token.type)
    }

    @Test
    fun `Given no session cookie, Then it returns a null identity (anonymous)`() {
        assertNull(mechanism.authenticate(contextWithCookie(null), idpManager).await().indefinitely())
    }

    @Test
    fun `Given an empty session cookie, Then it returns a null identity (anonymous)`() {
        // A cleared cookie the browser still sends back is not a credential.
        assertNull(mechanism.authenticate(contextWithCookie(""), idpManager).await().indefinitely())
    }

    @Test
    fun `Given getChallenge, Then it is a bare 401`() {
        // Given / When
        val challenge = mechanism.getChallenge(contextWithCookie(null)).await().indefinitely()

        // Then
        assertEquals(401, challenge.status)
    }

    @Test
    fun `Given getCredentialTypes, Then it is TokenAuthenticationRequest`() {
        assertTrue(mechanism.getCredentialTypes().contains(TokenAuthenticationRequest::class.java))
    }

    @Test
    fun `Given getCredentialTransport, Then it names the cookie`() {
        // Given / When
        val transport = mechanism.getCredentialTransport(contextWithCookie(null)).await().indefinitely()

        // Then
        assertEquals(SessionCookie.NAME, transport.authenticationScheme)
    }

    @Test
    fun `Given both mechanisms, Then the cookie runs after the header`() {
        // Quarkus asks mechanisms in descending priority, so a request holding both resolves as the
        // header's session and never as the cookie's.
        assertTrue(mechanism.priority < BearerAuthenticationMechanism().priority)
        assertEquals(HttpAuthenticationMechanism.DEFAULT_PRIORITY, BearerAuthenticationMechanism().priority)
    }
}
