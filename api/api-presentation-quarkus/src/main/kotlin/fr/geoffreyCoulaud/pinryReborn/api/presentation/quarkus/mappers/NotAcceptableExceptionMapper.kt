package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import jakarta.ws.rs.NotAcceptableException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** A `406`: the route produces none of the `Accept` types. Raised against a declared `@Produces` only. */
@Provider
class NotAcceptableExceptionMapper : ExceptionMapper<NotAcceptableException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: NotAcceptableException): Response =
        ProblemResponses.problemResponse(
            Response.Status.NOT_ACCEPTABLE,
            "The route produces none of the Accept types",
            ProblemCode.NOT_ACCEPTABLE,
            uriInfo,
        ).build()
}
