package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** The fallback: a `500` logged at ERROR with its stack, whose body says nothing of the cause. */
@Provider
class ThrowableMapper : ExceptionMapper<Throwable> {
    private val logger = KotlinLogging.logger {}

    @Context lateinit var uriInfo: UriInfo

    override fun toResponse(exception: Throwable): Response {
        logger.error(exception) { "Request to ${uriInfo.path} failed" }
        return ProblemResponses.internalError(uriInfo).build()
    }
}
