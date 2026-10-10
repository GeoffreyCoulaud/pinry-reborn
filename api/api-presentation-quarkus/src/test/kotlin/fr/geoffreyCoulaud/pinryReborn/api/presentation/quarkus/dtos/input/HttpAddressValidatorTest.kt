package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpAddressValidatorTest {
    private val validator = HttpAddressValidator()

    private fun accepts(value: String?) = validator.isValid(value, mockk())

    @Test
    fun `Given no address, Then the validator leaves its absence to the type`() {
        assertEquals(true, accepts(null))
    }

    @Test
    fun `Given an absolute https address in upper case, Then the validator accepts it`() {
        assertEquals(true, accepts("HTTPS://X.test/a"))
    }

    @Test
    fun `Given a blank text, a text that is no address, or an ftp address, Then the validator refuses each`() {
        assertEquals(listOf(false, false, false), listOf("", "not an address", "ftp://x.test/").map(::accepts))
    }
}
