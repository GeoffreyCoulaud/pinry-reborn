package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.quarkus.runtime.configuration.MemorySize
import io.quarkus.vertx.http.runtime.RouteConstants
import io.vertx.core.Handler
import io.vertx.core.http.HttpConnection
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger

class OversizeBodyRefusalTest {
    private val objectMapper = ObjectMapper()
    private val refusal = OversizeBodyRefusal(objectMapper, MemorySize(BigInteger.valueOf(LIMIT)))

    private val connection = mockk<HttpConnection>(relaxed = true)
    private val response = mockk<HttpServerResponse> {
        every { setStatusCode(any()) } returns this
        every { putHeader(any<CharSequence>(), any<CharSequence>()) } returns this
        every { endHandler(any()) } returns this
        every { end(any<String>()) } returns mockk()
    }

    private fun context(contentLength: String?): RoutingContext {
        val request = mockk<HttpServerRequest> {
            every { getHeader(HttpHeaders.CONTENT_LENGTH) } returns contentLength
            every { connection() } returns connection
        }
        return mockk(relaxed = true) {
            every { normalizedPath() } returns PATH
            every { request() } returns request
            every { response() } returns response
        }
    }

    @Test
    fun `Given the router, Then the route runs just ahead of Quarkus's own limit`() {
        // Given
        val route = mockk<Route>(relaxed = true)
        val router = mockk<Router> { every { route() } returns route }
        every { route.order(any()) } returns route

        // When
        refusal.register(router)

        // Then
        verify { route.order(RouteConstants.ROUTE_ORDER_UPLOAD_LIMIT - 1) }
    }

    @Test
    fun `Given no Content-Length, Then the request goes on to the chunked limit`() {
        // Given
        val context = context(contentLength = null)

        // When
        refusal.handle(context)

        // Then
        verify { context.next() }
    }

    @Test
    fun `Given a Content-Length that is not a number, Then the request goes on to the framework`() {
        // Given
        val context = context(contentLength = "many")

        // When
        refusal.handle(context)

        // Then
        verify { context.next() }
    }

    @Test
    fun `Given a Content-Length at the limit, Then the request goes on`() {
        // Given
        val context = context(contentLength = LIMIT.toString())

        // When
        refusal.handle(context)

        // Then
        verify { context.next() }
    }

    @Test
    fun `Given a Content-Length past the limit, Then a 413 problem answers and the connection closes`() {
        // Given
        val context = context(contentLength = (LIMIT + 1).toString())
        val body = slot<String>()
        val onEnd = slot<Handler<Void>>()
        every { response.end(capture(body)) } returns mockk()
        every { response.endHandler(capture(onEnd)) } returns response

        // When
        refusal.handle(context)
        onEnd.captured.handle(null)

        // Then
        val problem = objectMapper.readTree(body.captured)
        assertEquals("BODY_TOO_LARGE", problem["code"].asText())
        assertEquals(413, problem["status"].asInt())
        assertEquals(PATH, problem["instance"].asText())
        verify { response.setStatusCode(413) }
        verify { response.putHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json") }
        verify { connection.close() }
        verify(exactly = 0) { context.next() }
    }

    private companion object {
        const val LIMIT = 1024L
        const val PATH = "/api/v1/me/imports/some-id/archive"
    }
}
