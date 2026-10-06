package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.core.JacksonException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.io.EOFException
import java.io.IOException
import java.nio.channels.ClosedChannelException

/**
 * A `500` logged by whose failure it is: Jackson's exceptions are `IOException`s and land here first, the server's
 * (ERROR); a connection the client closed is noise (DEBUG); any other I/O failure, a disk's (WARN).
 */
@Provider
class IOExceptionMapper : ExceptionMapper<IOException> {
    private val logger = KotlinLogging.logger {}

    @Context lateinit var uriInfo: UriInfo

    override fun toResponse(exception: IOException): Response {
        when (exception) {
            is JacksonException -> logger.error(exception) { "Jackson failed on ${uriInfo.path}" }
            is EOFException,
            is ClosedChannelException -> logger.debug(exception) { "the client left ${uriInfo.path}" }
            else -> logger.warn(exception) { "I/O failed on ${uriInfo.path}" }
        }
        return ProblemResponses.internalError(uriInfo).build()
    }
}
