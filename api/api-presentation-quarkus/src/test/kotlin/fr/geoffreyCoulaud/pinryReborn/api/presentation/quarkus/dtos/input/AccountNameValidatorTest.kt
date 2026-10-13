package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccountNameValidatorTest {
    @Test
    fun `Given null, an account name, two characters and a space, Then only the first two pass`() {
        val values = listOf(null, "Alice.B", "ab", "a b")

        val accepted = values.map { AccountNameValidator().isValid(it, mockk()) }

        assertEquals(listOf(true, true, false, false), accepted)
    }
}
