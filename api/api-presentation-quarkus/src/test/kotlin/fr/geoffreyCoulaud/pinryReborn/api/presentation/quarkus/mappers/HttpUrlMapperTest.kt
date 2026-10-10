package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.HttpUrlMapper.toHttpUrl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpUrlMapperTest {
    @Test
    fun `Given a text the constraint should have refused, Then the mapper throws rather than invent an address`() {
        assertThrows<IllegalStateException> { "not an address".toHttpUrl() }
    }
}
