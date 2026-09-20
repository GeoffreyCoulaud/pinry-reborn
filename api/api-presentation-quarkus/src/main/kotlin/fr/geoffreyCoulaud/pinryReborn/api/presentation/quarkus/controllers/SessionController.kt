package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.IssuedSession
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.SessionTransportDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.SessionCreationInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.CreatedSessionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ExistingSessionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.SessionDtoMapper.toCreatedDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.SessionDtoMapper.toExistingDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.SessionCookie
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getSessionToken
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getSessionTransport
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SessionCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SessionRenewer
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SessionRevoker
import fr.geoffreyCoulaud.pinryReborn.api.domain.security.SessionExpiryPolicy
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.UserAuthenticationError
import io.quarkus.security.AuthenticationFailedException
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse

@Path("/api/v1/sessions")
class SessionController(
    private val sessionCreator: SessionCreator,
    private val sessionRenewer: SessionRenewer,
    private val sessionRevoker: SessionRevoker,
    private val expiryPolicy: SessionExpiryPolicy,
    private val securityIdentity: SecurityIdentity,
) {
    // SmallRye reads one status off the return type, and these two routes answer two; the status is
    // what tells the transports apart, so no field of either body is ever nullable.
    @POST
    @PermitAll
    @APIResponse(responseCode = "201", description = BEARER_ANSWER,
        content = [Content(mediaType = JSON, schema = Schema(implementation = CreatedSessionOutputDto::class))])
    @APIResponse(responseCode = "200", description = COOKIE_ANSWER,
        content = [Content(mediaType = JSON, schema = Schema(implementation = ExistingSessionOutputDto::class))])
    @APIResponse(responseCode = "401", description = AUTHENTICATION_FAILED,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))])
    fun createSession(@Valid @NotNull dto: SessionCreationInputDto): RestResponse<Any> {
        val persistent = dto.rememberMe ?: false
        val issued = try {
            sessionCreator.create(name = dto.name, password = dto.password, persistent = persistent)
        } catch (e: UserAuthenticationError) {
            throw AuthenticationFailedException("Authentication failed", e)
        }
        return sessionResponse(dto.transport, issued, persistent)
    }

    @GET
    @Path("/current")
    @Authenticated
    fun getCurrentSession(): ExistingSessionOutputDto {
        val current = securityIdentity.getSessionToken()
        return current.toExistingDto(expiryPolicy.renewAfterFor(current.expiresAt, current.persistent))
    }

    @POST
    @Path("/current/renew")
    @Authenticated
    @APIResponse(responseCode = "201", description = BEARER_ANSWER,
        content = [Content(mediaType = JSON, schema = Schema(implementation = CreatedSessionOutputDto::class))])
    @APIResponse(responseCode = "200", description = COOKIE_ANSWER,
        content = [Content(mediaType = JSON, schema = Schema(implementation = ExistingSessionOutputDto::class))])
    fun renewSession(): RestResponse<Any> {
        val current = securityIdentity.getSessionToken()
        val renewed = sessionRenewer.renew(current)
        return sessionResponse(securityIdentity.getSessionTransport(), renewed, current.persistent)
    }

    @DELETE
    @Path("/current")
    @Authenticated
    @APIResponse(responseCode = "204", description = REVOKED)
    fun revokeCurrentSession(): RestResponse<Void> {
        sessionRevoker.revokeCurrent(securityIdentity.getSessionToken())
        return revocationResponse()
    }

    @DELETE
    @Authenticated
    @APIResponse(responseCode = "204", description = REVOKED)
    fun revokeAllSessions(): RestResponse<Void> {
        sessionRevoker.revokeAll(securityIdentity.getUser())
        return revocationResponse()
    }

    /** The token itself for a bearer session; the cookie and no token for a cookie one. */
    private fun sessionResponse(
        transport: SessionTransportDto,
        issued: IssuedSession,
        persistent: Boolean,
    ): RestResponse<Any> {
        val response = when (transport) {
            SessionTransportDto.BEARER ->
                RestResponse.ResponseBuilder.create<Any>(RestResponse.Status.CREATED, issued.toCreatedDto())

            SessionTransportDto.COOKIE ->
                RestResponse.ResponseBuilder
                    .create<Any>(RestResponse.Status.OK, issued.toExistingDto(persistent))
                    .cookie(SessionCookie.issued(issued.token, issued.expiresAt, persistent))
        }
        return response.header(CACHE_CONTROL_HEADER, NO_STORE).build()
    }

    /** A cookie session leaves with its cookie cleared; a bearer one carries nothing to clear. */
    private fun revocationResponse(): RestResponse<Void> {
        val response = RestResponse.ResponseBuilder.create<Void>(RestResponse.Status.NO_CONTENT)
        if (securityIdentity.getSessionTransport() == SessionTransportDto.COOKIE) {
            response.cookie(SessionCookie.cleared())
        }
        return response.build()
    }

    private companion object {
        const val CACHE_CONTROL_HEADER = "Cache-Control"
        const val NO_STORE = "no-store"
        const val JSON = MediaType.APPLICATION_JSON
        const val BEARER_ANSWER = "Bearer session, with the token the client sends back as a header"
        const val COOKIE_ANSWER = "Cookie session, carried by the pinry_session cookie and never in the body"
        const val REVOKED = "Session revoked, and the pinry_session cookie cleared when the request carried one"
        const val AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED: the name is unknown, the password is wrong, " +
            "or the account is held closed by the attempt limiter"
    }
}
