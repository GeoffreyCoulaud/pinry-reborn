package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MultivaluedMap
import org.jboss.resteasy.reactive.server.ServerRequestFilter
import org.jboss.resteasy.reactive.server.ServerResponseFilter

/** Logs each request line, response status and headers; never a body, and never a credential header's value. */
class LoggingRequestResponseFilter(private val objectMapper: ObjectMapper) {
    private val logger = KotlinLogging.logger {}

    @ServerRequestFilter
    fun requestFilter(ctx: ContainerRequestContext) {
        logger.info { "In --> ${ctx.method.uppercase()} ${ctx.uriInfo.requestUri}" }
        logHeaders(ctx.headers)
    }

    @ServerResponseFilter
    fun responseFilter(ctx: ContainerResponseContext) {
        logger.info { "Out --> ${ctx.status}" }
        logHeaders(ctx.headers)
    }

    private fun logHeaders(headers: MultivaluedMap<String, out Any>) {
        val headersMap =
            headers.entries.associate { (name, values) ->
                name to if (CREDENTIAL_HEADERS.any { it.equals(name, ignoreCase = true) }) REDACTED else values
            }
        val headersString = objectMapper.writeValueAsString(headersMap)
        logger.info { "Headers: $headersString" }
    }

    private companion object {
        val CREDENTIAL_HEADERS = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.SET_COOKIE)
        const val REDACTED = "<redacted>"
    }
}
