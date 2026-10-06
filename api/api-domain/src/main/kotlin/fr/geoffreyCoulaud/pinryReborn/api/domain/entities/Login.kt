package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/**
 * A login is something that when given to the server authenticates a user. Common examples include:
 * - Username & password
 * - A server-side session token
 * - A signed OIDC token pair
 */
sealed interface Login {
    /** Basic Auth is a username + password login, without any token. */
    class BasicAuthLogin(
        val userName: String,
        val password: String,
    ) : Login
}
