package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.SessionTokenExpiredError
import io.quarkus.security.AuthenticationFailedException
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
@Priority(Priorities.AUTHENTICATION)
class AuthenticationFailedExceptionMapper : ExceptionMapper<AuthenticationFailedException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: AuthenticationFailedException): Response {
        val (code, detail) = describe(exception)
        return ProblemResponses.problemResponse(
            status = Response.Status.UNAUTHORIZED,
            detail = detail,
            code = code,
            uriInfo = uriInfo,
        ).header("WWW-Authenticate", ProblemResponses.WWW_AUTHENTICATE_BEARER).build()
    }

    // Cause-inspection lives here (not in a mapped subtype): a subtype of the final
    // AuthenticationFailedException never reached this chain at runtime (see BearerTokenIdentityProvider).
    private fun describe(exception: AuthenticationFailedException): Pair<ProblemCode, String> =
        if (exception.cause is SessionTokenExpiredError) {
            ProblemCode.SESSION_EXPIRED to "Session expired"
        } else {
            ProblemCode.AUTHENTICATION_FAILED to "Authentication failed"
        }
}
