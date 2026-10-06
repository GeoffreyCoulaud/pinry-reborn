package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.core.UriInfo
import java.io.EOFException
import java.io.IOException
import java.nio.channels.ClosedChannelException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IOExceptionMapperTest {
    private val mapper =
        IOExceptionMapper().apply {
            uriInfo = mockk<UriInfo> { every { path } returns "/api/v1/pins" }
        }

    private fun assertInternalError(exception: IOException) {
        val response = mapper.toResponse(exception)
        val problem = response.entity as ProblemDetail
        assertEquals(500, response.status)
        assertEquals("INTERNAL_ERROR", problem.code)
        assertNull(problem.detail)
    }

    @Test
    fun `Given a Jackson definition problem, an IOException by inheritance, Then it is a 500 with no detail`() {
        assertInternalError(InvalidDefinitionException.from(null as JsonParser?, "no creator", null as JavaType?))
    }

    @Test
    fun `Given a connection the client closed, Then it is a 500 with no detail`() {
        assertInternalError(EOFException("Connection reset"))
        assertInternalError(ClosedChannelException())
    }

    @Test
    fun `Given any other IO failure, Then it is a 500 with no detail`() {
        assertInternalError(IOException("No space left on device"))
    }
}
