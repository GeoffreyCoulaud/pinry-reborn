package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.core.JsonProcessingException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * The umbrella: a `WebApplicationException` no other mapper names keeps its own status. The Jackson
 * reader wraps a parse failure in a bare `400` carrying it as cause, the body row's only way here.
 */
@Provider
class WebApplicationExceptionMapper : ExceptionMapper<WebApplicationException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: WebApplicationException): Response {
        val cause = exception.cause
        if (cause is JsonProcessingException) return ProblemResponses.malformedBody(cause, uriInfo).build()
        val status = exception.response.status
        val named = Response.Status.fromStatusCode(status)
        val title = if (named == null) "HTTP $status" else named.reasonPhrase
        return ProblemResponses
            .problemResponse(status, title, exception.message, ProblemCode.HTTP_ERROR, uriInfo)
            .build()
    }
}
