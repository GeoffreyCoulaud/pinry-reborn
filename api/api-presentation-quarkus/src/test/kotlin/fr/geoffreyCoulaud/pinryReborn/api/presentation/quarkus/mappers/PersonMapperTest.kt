package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PersonInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PersonMapper.toReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PersonMapperTest {
    @Test
    fun `Given a name the constraints should have refused, Then the mapper throws rather than invent a name`() {
        assertThrows<IllegalStateException> { PersonInputDto(name = " ", urls = emptyList()).toReference() }
    }
}
