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
        // Given: a bodyless 403 a controller declares stays, for the contract test to refuse
        val smallRye = OASFactory.createAPIResponse().description("Not Allowed")
        val declaredBodyless = OASFactory.createAPIResponse().description("Another account's pin")
        val referenced = OASFactory.createAPIResponse().ref(SharedRefusalsFilter.PIN_FORBIDDEN)
        val inline = OASFactory.createAPIResponse().content(OASFactory.createContent())
        val namedLikeSmallRye = listOf(
            OASFactory.createAPIResponse().description("Not Allowed").content(OASFactory.createContent()),
            OASFactory.createAPIResponse().description("Not Allowed").ref(SharedRefusalsFilter.PIN_FORBIDDEN),
        )
        val declared = listOf(declaredBodyless, referenced, inline) + namedLikeSmallRye

        // When
        val kept = (listOf(smallRye) + declared).map { response ->
            filter.filterOperation(
                OASFactory.createOperation().responses(OASFactory.createAPIResponses().addAPIResponse("403", response)),
            ).responses.getAPIResponse("403")
        }

        // Then
        assertEquals(listOf(null) + declared, kept)
    }

    @Test
    fun `Given an operation reading a body and declaring no 413, Then its 413 points at the shared entry`() {
        // Given
        val operation = OASFactory.createOperation()
            .requestBody(OASFactory.createRequestBody())
            .responses(OASFactory.createAPIResponses())

        // When
        val filtered = filter.filterOperation(operation)

        // Then
        assertEquals(
            "#/components/responses/${SharedRefusalsFilter.BODY_TOO_LARGE}",
            filtered.responses.getAPIResponse("413").ref,
        )
    }

    @Test
    fun `Given an operation reading a body with a 413 of its own, Then BODY_TOO_LARGE joins its codes once`() {
        // Given
        val code = OASFactory.createSchema().enumeration(listOf("IMAGE_TOO_LARGE"))
        val own = OASFactory.createAPIResponse().content(
            OASFactory.createContent().addMediaType(
                "application/problem+json",
                OASFactory.createMediaType().schema(OASFactory.createSchema().addProperty("code", code)),
            ),
        )
        val operation = OASFactory.createOperation()
            .requestBody(OASFactory.createRequestBody())
            .responses(OASFactory.createAPIResponses().addAPIResponse("413", own))

        // When
        filter.filterOperation(filter.filterOperation(operation))

        // Then
        assertEquals(listOf("IMAGE_TOO_LARGE", "BODY_TOO_LARGE"), code.enumeration)
    }

    @Test
    fun `Given an operation reading no body, Then it gets no 413`() {
        // Given
        val operation = OASFactory.createOperation().responses(OASFactory.createAPIResponses())

        // When / Then
        assertNull(filter.filterOperation(operation).responses.getAPIResponse("413"))
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
