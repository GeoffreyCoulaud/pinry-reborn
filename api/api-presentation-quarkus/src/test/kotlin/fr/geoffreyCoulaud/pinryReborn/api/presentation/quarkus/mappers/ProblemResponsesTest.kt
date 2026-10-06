package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.core.UriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProblemResponsesTest {
    private val uriInfo = mockk<UriInfo> { every { path } returns "/api/v1/boards" }

    private fun mismatchedInput(): MismatchedInputException =
        MismatchedInputException.from(null as JsonParser?, String::class.java, "boom")

    @Test
    fun `Given a binding failure under a property, Then the detail names the property and nothing else`() {
        // Given: the innermost reference is prepended first, so the path reads tags[2]
        val exception =
            mismatchedInput().apply {
                prependPath(Any(), 2)
                prependPath(Any(), "tags")
            }

        // When
        val problem = ProblemResponses.malformedBody(exception, uriInfo).build().entity as ProblemDetail

        // Then
        assertEquals(400, problem.status)
        assertEquals("MALFORMED_BODY", problem.code)
        assertEquals("The body could not be read at `tags[2]`", problem.detail)
    }

    @Test
    fun `Given a binding failure at the root, Then the detail says so`() {
        // Given
        val exception = mismatchedInput()

        // When
        val problem = ProblemResponses.malformedBody(exception, uriInfo).build().entity as ProblemDetail

        // Then
        assertEquals("The body could not be read at its root", problem.detail)
    }

    @Test
    fun `Given a parse failure, Then the detail carries the parser's message without its location`() {
        // Given
        val exception = JsonParseException(null as JsonParser?, "Unexpected character ('n' (code 110))")

        // When
        val problem = ProblemResponses.malformedBody(exception, uriInfo).build().entity as ProblemDetail

        // Then
        assertEquals("The body is not valid JSON: Unexpected character ('n' (code 110))", problem.detail)
    }

    @Test
    fun `Given an internal error, Then the payload is 500 INTERNAL_ERROR with no detail`() {
        // When
        val response = ProblemResponses.internalError(uriInfo).build()
        val problem = response.entity as ProblemDetail

        // Then
        assertEquals(500, response.status)
        assertEquals(ProblemResponses.PROBLEM_JSON_MEDIA_TYPE, response.mediaType.toString())
        assertEquals("INTERNAL_ERROR", problem.code)
        assertNull(problem.detail)
        assertEquals("/api/v1/boards", problem.instance)
    }
}
