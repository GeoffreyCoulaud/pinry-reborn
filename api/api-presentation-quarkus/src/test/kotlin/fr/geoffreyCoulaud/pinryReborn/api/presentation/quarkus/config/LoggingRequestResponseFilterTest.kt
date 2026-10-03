package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.UriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class LoggingRequestResponseFilterTest {
    private val objectMapper = spyk(ObjectMapper())
    private val filter = LoggingRequestResponseFilter(objectMapper = objectMapper)

    private fun request(headers: Map<String, String>): ContainerRequestContext {
        val ctx = mockk<ContainerRequestContext>()
        val uriInfo = mockk<UriInfo>()
        every { ctx.method } returns "POST"
        every { ctx.uriInfo } returns uriInfo
        every { uriInfo.requestUri } returns URI.create("http://localhost/api/v1/sessions")
        every { ctx.headers } returns MultivaluedHashMap(headers)
        return ctx
    }

    private fun response(headers: Map<String, Any>): ContainerResponseContext {
        val ctx = mockk<ContainerResponseContext>()
        every { ctx.status } returns 201
        every { ctx.headers } returns MultivaluedHashMap(headers)
        return ctx
    }

    /** The one header map the filter handed to the serializer. */
    private fun loggedHeaders(): Any? {
        val serialized = mutableListOf<Any?>()
        verify { objectMapper.writeValueAsString(captureNullable(serialized)) }
        return serialized.single()
    }

    @Test
    fun `Given a request with a body, Then requestFilter never reads its entity stream`() {
        // Given
        val ctx = request(mapOf("Content-Type" to "application/json"))

        // When
        filter.requestFilter(ctx)

        // Then
        verify(exactly = 0) { ctx.entityStream }
    }

    @Test
    fun `Given Authorization and Cookie request headers, Then requestFilter redacts them and keeps the rest`() {
        // Given
        val ctx = request(
            mapOf("Authorization" to "Bearer a-token", "Cookie" to "pinry_session=a-token", "Accept" to "*/*"),
        )

        // When
        filter.requestFilter(ctx)

        // Then
        assertEquals(
            mapOf("Authorization" to "<redacted>", "Cookie" to "<redacted>", "Accept" to listOf("*/*")),
            loggedHeaders(),
        )
    }

    @Test
    fun `Given a credential header named in lower case, Then requestFilter still logs it redacted`() {
        // Given
        val ctx = request(mapOf("authorization" to "Bearer a-token"))

        // When
        filter.requestFilter(ctx)

        // Then
        assertEquals(mapOf("authorization" to "<redacted>"), loggedHeaders())
    }

    @Test
    fun `Given a response setting a cookie, Then responseFilter logs the Set-Cookie header redacted`() {
        // Given
        val ctx = response(mapOf("Set-Cookie" to NewCookie.Builder("pinry_session").value("a-token").build()))

        // When
        filter.responseFilter(ctx)

        // Then
        assertEquals(mapOf("Set-Cookie" to "<redacted>"), loggedHeaders())
    }

    @Test
    fun `Given a response with an entity, Then responseFilter never reads it`() {
        // Given
        val ctx = response(mapOf("Content-Type" to "application/json"))

        // When
        filter.responseFilter(ctx)

        // Then
        verify(exactly = 0) { ctx.entity }
    }
}
