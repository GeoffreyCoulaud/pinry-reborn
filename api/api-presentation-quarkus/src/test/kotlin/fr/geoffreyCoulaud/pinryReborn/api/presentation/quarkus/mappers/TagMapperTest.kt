package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.TagMapper.toTagName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TagMapperTest {
    @Test
    fun `Given a tag name the constraints accept, Then the mapper keeps it as written`() {
        assertEquals("Landscape", "Landscape".toTagName().text)
    }

    @Test
    fun `Given a tag name the constraints should have refused, Then the mapper throws rather than invent a name`() {
        assertThrows<IllegalStateException> { " ".toTagName() }
    }
}
