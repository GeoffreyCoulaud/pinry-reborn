package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * The one Jackson read failure the reader rethrows as itself. Quarkus's own mapper for it is disabled in
 * `application.properties`: resolution takes the exact class before any parent (docs/adr/0021).
 */
@Provider
class MismatchedInputExceptionMapper : ExceptionMapper<MismatchedInputException> {
    @Context lateinit var uriInfo: UriInfo

    override fun toResponse(exception: MismatchedInputException): Response =
        ProblemResponses.malformedBody(exception, uriInfo).build()
}
