package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security

import jakarta.ws.rs.core.NewCookie
import java.time.Instant
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCookieTest {
    private val expiresAt = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun `Given a persistent session, Then the cookie expires with it`() {
        // Given / When
        val cookie = SessionCookie.issued(TOKEN, expiresAt, persistent = true)

        // Then
        assertEquals(TOKEN, cookie.value)
        assertEquals(Date.from(expiresAt), cookie.expiry)
    }

    @Test
    fun `Given an ephemeral session, Then the cookie carries no expiry and the browser drops it on close`() {
        // Given / When
        val cookie = SessionCookie.issued(TOKEN, expiresAt, persistent = false)

        // Then
        assertEquals(TOKEN, cookie.value)
        assertNull(cookie.expiry)
        assertEquals(NewCookie.DEFAULT_MAX_AGE, cookie.maxAge)
    }

    @Test
    fun `Given a revocation, Then the cleared cookie carries no value and expires at once`() {
        // Given / When
        val cookie = SessionCookie.cleared()

        // Then
        assertEquals("", cookie.value)
        assertEquals(0, cookie.maxAge)
    }

    @Test
    fun `Given any session cookie, Then it is HttpOnly, Secure, SameSite Strict and rooted at the origin`() {
        // Given / When
        val cookies = listOf(SessionCookie.issued(TOKEN, expiresAt, persistent = true), SessionCookie.cleared())

        // Then
        cookies.forEach { cookie ->
            assertEquals(SessionCookie.NAME, cookie.name)
            assertEquals("/", cookie.path)
            assertTrue(cookie.isHttpOnly, "A script that can read the token defeats the transport")
            assertTrue(cookie.isSecure)
            assertEquals(NewCookie.SameSite.STRICT, cookie.sameSite)
        }
    }

    private companion object {
        const val TOKEN = "a-session-token"
    }
}
