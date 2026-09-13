package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.SessionTransportDto
import io.quarkus.security.credential.TokenCredential
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.AuthenticationRequest
import io.quarkus.security.identity.request.TokenAuthenticationRequest
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.core.Response

/**
 * The same session token as [BearerAuthenticationMechanism], read from the `pinry_session` cookie.
 * Only the extraction forks: both build a [TokenAuthenticationRequest] for the one identity provider.
 */
@ApplicationScoped
class CookieAuthenticationMechanism : HttpAuthenticationMechanism {
    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager,
    ): Uni<SecurityIdentity> {
        val token = context.request().getCookie(SessionCookie.NAME)?.value
        if (token.isNullOrEmpty()) {
            return Uni.createFrom().nullItem()
        }
        return identityProviderManager.authenticate(
            TokenAuthenticationRequest(TokenCredential(token, SessionTransportDto.COOKIE.credentialType)),
        )
    }

    /** A cookie names no authentication scheme, so the challenge carries a status and nothing else. */
    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(Response.Status.UNAUTHORIZED.statusCode, null, null))

    override fun getCredentialTypes(): Set<Class<out AuthenticationRequest>> =
        setOf(TokenAuthenticationRequest::class.java)

    override fun getCredentialTransport(context: RoutingContext): Uni<HttpCredentialTransport> =
        Uni.createFrom().item(HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE, SessionCookie.NAME))

    /**
     * Below the header's, which stays at Quarkus's default: mechanisms are asked in descending
     * priority, so a request carrying both credentials authenticates as the header's.
     */
    override fun getPriority(): Int = HttpAuthenticationMechanism.DEFAULT_PRIORITY - 1
}
