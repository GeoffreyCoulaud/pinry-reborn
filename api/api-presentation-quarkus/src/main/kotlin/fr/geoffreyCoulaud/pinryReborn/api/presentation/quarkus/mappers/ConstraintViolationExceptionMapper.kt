package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class ConstraintViolationExceptionMapper : ExceptionMapper<ConstraintViolationException> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: ConstraintViolationException): Response {
        val detail = exception.constraintViolations
            .joinToString(separator = "; ") { "${it.propertyPath}: ${it.message}" }
        return ProblemResponses
            .problemResponse(Response.Status.BAD_REQUEST, detail, ProblemCode.VALIDATION_ERROR, uriInfo)
            .build()
    }
}
