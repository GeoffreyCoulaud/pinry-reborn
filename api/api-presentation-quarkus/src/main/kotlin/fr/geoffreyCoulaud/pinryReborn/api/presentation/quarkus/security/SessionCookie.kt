package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security

import jakarta.ws.rs.core.NewCookie
import java.time.Instant
import java.util.Date

/**
 * The `pinry_session` cookie. `SameSite=Strict` is what closes CSRF, and it suffices because one reverse proxy puts the
 * web application on the API's own origin (`docs/adr/0026-one-session-two-transports.md`).
 */
object SessionCookie {
    const val NAME = "pinry_session"

    /**
     * The cookie carrying [token]. A persistent session expires with the token; an ephemeral one carries no expiry at
     * all, so the browser drops it when it closes.
     */
    fun issued(token: String, expiresAt: Instant, persistent: Boolean): NewCookie {
        val cookie = attributes().value(token)
        if (persistent) cookie.expiry(Date.from(expiresAt))
        return cookie.build()
    }

    /** The same cookie, already expired: what a revocation sends back. */
    fun cleared(): NewCookie = attributes().value("").maxAge(0).build()

    private fun attributes(): NewCookie.Builder =
        NewCookie.Builder(NAME).path("/").httpOnly(true).secure(true).sameSite(NewCookie.SameSite.STRICT)
}
