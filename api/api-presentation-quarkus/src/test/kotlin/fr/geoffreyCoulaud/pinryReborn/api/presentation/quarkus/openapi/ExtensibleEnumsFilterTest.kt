package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import org.eclipse.microprofile.openapi.OASFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExtensibleEnumsFilterTest {
    private val filter = ExtensibleEnumsFilter()

    @Test
    fun `Given each open code's component as SmallRye writes it, Then its values move to x-extensible-enum`() {
        // Given: the closed enum SmallRye writes for every Kotlin enum, plus one component left alone
        val values = listOf("A", "B")
        val components = OASFactory.createComponents()
        ExtensibleEnumsFilter.EXTENSIBLE.forEach {
            components.addSchema(it, OASFactory.createSchema().enumeration(values))
        }
        components.addSchema("DownloadStatusDto", OASFactory.createSchema().enumeration(values))
        val openAPI = OASFactory.createOpenAPI().components(components)

        // When
        filter.filterOpenAPI(openAPI)

        // Then
        ExtensibleEnumsFilter.EXTENSIBLE.forEach {
            val schema = openAPI.components.schemas.getValue(it)
            assertNull(schema.enumeration, it)
            assertEquals(values, schema.extensions[ExtensibleEnumsFilter.EXTENSION], it)
        }
        assertEquals(values, openAPI.components.schemas.getValue("DownloadStatusDto").enumeration)
    }
}
