package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import org.eclipse.microprofile.openapi.OASFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionSecurityRequirementFilterTest {
    private val filter = SessionSecurityRequirementFilter()

    @Test
    fun `Given an operation SmallRye stamped, Then it names both transports, the header first`() {
        // Given: the lone requirement SmallRye writes, which names whichever scheme it picked
        val operation = OASFactory.createOperation()
            .addSecurityRequirement(OASFactory.createSecurityRequirement().addScheme("CookieScheme"))

        // When
        val filtered = filter.filterOperation(operation)

        // Then
        assertEquals(
            listOf(setOf("BearerScheme"), setOf("CookieScheme")),
            filtered.security.map { it.schemes.keys },
        )
    }

    @Test
    fun `Given an operation SmallRye left open, Then no scheme is added to it`() {
        // Given / When / Then: a @PermitAll route stays reachable without a credential
        assertNull(filter.filterOperation(OASFactory.createOperation()).security)
    }

    @Test
    fun `Given an operation whose requirement list is empty, Then no scheme is added to it`() {
        // Given
        val operation = OASFactory.createOperation().apply { security = emptyList() }

        // When / Then
        assertTrue(filter.filterOperation(operation).security.isEmpty())
    }
}
