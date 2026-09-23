package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** A `404` before any use case: no resource at the path, or a path or query value the framework could not read. */
@Provider
class NotFoundExceptionMapper : ExceptionMapper<NotFoundException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: NotFoundException): Response {
        // JAX-RS 3.2 makes a conversion failure a NotFoundException wrapping it; routing wraps nothing.
        val detail =
            if (exception.cause == null) "No resource serves this path" else "A path or query value could not be read"
        return ProblemResponses
            .problemResponse(Response.Status.NOT_FOUND, detail, ProblemCode.UNKNOWN_ROUTE, uriInfo)
            .build()
    }
}
