package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.HttpUrlModelMapper.toHttpUrl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpUrlModelMapperTest {
    @Test
    fun `Given a stored text the factory refuses, Then the mapper throws rather than drop the address`() {
        assertThrows<IllegalStateException> { "not an address".toHttpUrl() }
    }
}
