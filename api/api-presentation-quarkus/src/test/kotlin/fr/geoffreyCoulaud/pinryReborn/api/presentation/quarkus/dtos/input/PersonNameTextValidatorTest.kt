package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonNameTextValidatorTest {
    @Test
    fun `Given null, a name, a no-break space alone and 201 characters, Then only the first two pass`() {
        val values = listOf(null, "Alice", " ", "n".repeat(201))

        val accepted = values.map { PersonNameTextValidator().isValid(it, mockk()) }

        assertEquals(listOf(true, true, false, false), accepted)
    }
}
