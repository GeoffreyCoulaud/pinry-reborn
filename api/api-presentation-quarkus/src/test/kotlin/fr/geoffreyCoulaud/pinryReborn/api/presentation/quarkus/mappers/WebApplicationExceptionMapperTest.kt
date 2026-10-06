package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.ServiceUnavailableException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebApplicationExceptionMapperTest {
    private val mapper =
        WebApplicationExceptionMapper().apply {
            uriInfo = mockk<UriInfo> { every { path } returns "/api/v1/sessions" }
        }

    @Test
    fun `Given a bare 400 wrapping a Jackson failure, Then it maps to 400 MALFORMED_BODY`() {
        // Given: the shape ServerJacksonMessageBodyReader throws for a parse failure
        val cause = JsonParseException(null as JsonParser?, "bad")
        val exception = WebApplicationException(cause, Response.Status.BAD_REQUEST)

        // When
        val response = mapper.toResponse(exception)

        // Then
        assertEquals(400, response.status)
        assertEquals("MALFORMED_BODY", (response.entity as ProblemDetail).code)
    }

    @Test
    fun `Given an exception with a named status, Then it keeps the status under HTTP_ERROR with its title`() {
        // When
        val response = mapper.toResponse(ServiceUnavailableException())
        val problem = response.entity as ProblemDetail

        // Then
        assertEquals(503, response.status)
        assertEquals("HTTP_ERROR", problem.code)
        assertEquals("Service Unavailable", problem.title)
        assertEquals("HTTP 503 Service Unavailable", problem.detail)
    }

    @Test
    fun `Given a status with no constant, Then the title names the number`() {
        // When
        val response = mapper.toResponse(WebApplicationException(299))

        // Then
        assertEquals(299, response.status)
        assertEquals("HTTP 299", (response.entity as ProblemDetail).title)
    }
}
