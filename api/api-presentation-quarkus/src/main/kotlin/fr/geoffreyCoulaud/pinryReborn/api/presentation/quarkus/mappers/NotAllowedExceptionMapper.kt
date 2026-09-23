package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import jakarta.ws.rs.NotAllowedException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** A `405`: the path is served, not with this method. Quarkus REST names no `Allow` set, so none is published. */
@Provider
class NotAllowedExceptionMapper : ExceptionMapper<NotAllowedException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: NotAllowedException): Response =
        ProblemResponses.problemResponse(
            Response.Status.METHOD_NOT_ALLOWED,
            "The path is served, not with this method",
            ProblemCode.METHOD_NOT_ALLOWED,
            uriInfo,
        ).build()
}
