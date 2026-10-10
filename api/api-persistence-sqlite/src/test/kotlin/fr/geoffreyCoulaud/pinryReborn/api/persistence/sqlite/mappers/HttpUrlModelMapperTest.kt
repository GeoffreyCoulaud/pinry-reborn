package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.HttpUrlModelMapper.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpUrlModelMapperTest {
    @Test
    fun `Given a stored address, Then the mapper reads it back as written`() {
        assertEquals("https://x.test/a", "https://x.test/a".toHttpUrl().toString())
    }

    @Test
    fun `Given a stored text the factory refuses, Then the mapper throws rather than drop the address`() {
        assertThrows<IllegalStateException> { "not an address".toHttpUrl() }
    }
}
