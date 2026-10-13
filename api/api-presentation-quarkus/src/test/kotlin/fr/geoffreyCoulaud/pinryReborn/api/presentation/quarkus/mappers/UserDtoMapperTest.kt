package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.UserDtoMapper.toUsername
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserDtoMapperTest {
    @Test
    fun `Given an account name the constraints accept, Then the mapper keeps it as written`() {
        assertEquals("Alice.B", "Alice.B".toUsername().text)
    }

    @Test
    fun `Given an account name the constraints should have refused, Then the mapper throws rather than invent one`() {
        assertThrows<IllegalStateException> { "a b".toUsername() }
    }
}
