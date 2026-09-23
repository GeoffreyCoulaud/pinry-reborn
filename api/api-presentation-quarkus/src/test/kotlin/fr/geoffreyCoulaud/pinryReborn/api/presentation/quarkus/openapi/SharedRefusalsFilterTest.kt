package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import org.eclipse.microprofile.openapi.OASFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SharedRefusalsFilterTest {
    private val filter = SharedRefusalsFilter()

    @Test
    fun `Given a protected operation, Then its 401 points at the shared entry`() {
        // Given: the bodyless 401 SmallRye adds beside its requirement
        val operation = OASFactory.createOperation()
            .addSecurityRequirement(OASFactory.createSecurityRequirement().addScheme("BearerScheme"))
            .responses(
                OASFactory.createAPIResponses()
                    .addAPIResponse("401", OASFactory.createAPIResponse().description("Not Authorized")),
            )

        // When
        val filtered = filter.filterOperation(operation)

        // Then
        assertEquals(
            "#/components/responses/${SharedRefusalsFilter.UNAUTHENTICATED}",
            filtered.responses.getAPIResponse("401").ref,
        )
    }

    @Test
    fun `Given the bodyless 403 SmallRye adds, Then it is dropped and a declared 403 stays`() {
        // Given
        val bodyless = OASFactory.createAPIResponse().description("Not Allowed")
        val referenced = OASFactory.createAPIResponse().ref(SharedRefusalsFilter.PIN_FORBIDDEN)
        val inline = OASFactory.createAPIResponse().content(OASFactory.createContent())

        // When
        val kept = listOf(bodyless, referenced, inline).map { response ->
            filter.filterOperation(
                OASFactory.createOperation().responses(OASFactory.createAPIResponses().addAPIResponse("403", response)),
            ).responses.getAPIResponse("403")
        }

        // Then
        assertEquals(listOf(null, referenced, inline), kept)
    }

    @Test
    fun `Given an operation SmallRye left open, Then it gets no 401`() {
        // Given
        val operation = OASFactory.createOperation().responses(OASFactory.createAPIResponses())

        // When / Then
        assertNull(filter.filterOperation(operation).responses.getAPIResponse("401"))
    }

    @Test
    fun `Given the document, Then each shared entry carries its codes over the problem schema`() {
        // Given
        val openApi = OASFactory.createOpenAPI()

        // When
        filter.filterOpenAPI(openApi)

        // Then
        val schemas = openApi.components.responses.mapValues { (_, response) ->
            response.content.getMediaType("application/problem+json").schema
        }
        assertEquals(
            listOf("AUTHENTICATION_REQUIRED", "AUTHENTICATION_FAILED", "SESSION_EXPIRED"),
            schemas.getValue(SharedRefusalsFilter.UNAUTHENTICATED).properties.getValue("code").enumeration,
        )
        assertEquals(
            setOf("#/components/schemas/ProblemDetail"),
            schemas.values.map { it.allOf.single().ref }.toSet(),
        )
    }

    @Test
    fun `Given a document already holding components, Then the shared entries join them`() {
        // Given
        val openApi = OASFactory.createOpenAPI().components(
            OASFactory.createComponents().addSchema("ProblemDetail", OASFactory.createSchema()),
        )

        // When
        filter.filterOpenAPI(openApi)

        // Then
        assertEquals(setOf("ProblemDetail"), openApi.components.schemas.keys)
        assertEquals(true, openApi.components.responses.containsKey(SharedRefusalsFilter.UNAUTHENTICATED))
    }
}
