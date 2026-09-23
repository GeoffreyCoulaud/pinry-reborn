package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import jakarta.ws.rs.NotSupportedException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** A `415`: the request's `Content-Type` is none the route reads. */
@Provider
class NotSupportedExceptionMapper : ExceptionMapper<NotSupportedException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: NotSupportedException): Response =
        ProblemResponses.problemResponse(
            Response.Status.UNSUPPORTED_MEDIA_TYPE,
            "The route does not read this Content-Type",
            ProblemCode.UNSUPPORTED_MEDIA_TYPE,
            uriInfo,
        ).build()
}
