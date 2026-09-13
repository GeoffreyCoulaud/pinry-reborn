package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common

/**
 * How a session token travels. One session table and one identity provider, two vehicles: the header
 * the browser extension keeps, the cookie the web application uses (`docs/adr/0026-one-session-two-transports.md`).
 */
enum class SessionTransportDto(val credentialType: String) {
    BEARER("bearer"),
    COOKIE("cookie"),
    ;

    companion object {
        /** Anything the two mechanisms did not issue reads as the header's, which sends no cookie back. */
        fun ofCredential(credentialType: String): SessionTransportDto =
            if (credentialType == COOKIE.credentialType) COOKIE else BEARER
    }
}
