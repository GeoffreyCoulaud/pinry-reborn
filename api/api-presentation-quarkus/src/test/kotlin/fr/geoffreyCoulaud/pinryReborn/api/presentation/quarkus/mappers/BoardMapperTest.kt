package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.BoardMapper.toBoardName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BoardMapperTest {
    @Test
    fun `Given a board name the constraints accept, Then the mapper keeps it as written`() {
        assertEquals("Holidays", "Holidays".toBoardName().text)
    }

    @Test
    fun `Given a board name the constraints should have refused, Then the mapper throws rather than invent a name`() {
        assertThrows<IllegalStateException> { " ".toBoardName() }
    }
}
