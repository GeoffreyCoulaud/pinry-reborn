package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.UriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotFoundExceptionMapperTest {
    private val mapper =
        NotFoundExceptionMapper().apply {
            uriInfo = mockk<UriInfo> { every { path } returns "/api/v1/nowhere" }
        }

    @Test
    fun `Given no resource at the path, Then it maps to 404 UNKNOWN_ROUTE naming the path as unserved`() {
        // When
        val response = mapper.toResponse(NotFoundException("Unable to find matching target resource method"))
        val problem = response.entity as ProblemDetail

        // Then
        assertEquals(404, response.status)
        assertEquals("UNKNOWN_ROUTE", problem.code)
        assertEquals("No resource serves this path", problem.detail)
    }

    @Test
    fun `Given a path or query value the framework could not read, Then the detail says so`() {
        // Given: JAX-RS 3.2 wraps the conversion failure as the cause
        val exception = NotFoundException(IllegalArgumentException("not a UUID"))

        // When
        val problem = mapper.toResponse(exception).entity as ProblemDetail

        // Then
        assertEquals("UNKNOWN_ROUTE", problem.code)
        assertEquals("A path or query value could not be read", problem.detail)
    }
}
