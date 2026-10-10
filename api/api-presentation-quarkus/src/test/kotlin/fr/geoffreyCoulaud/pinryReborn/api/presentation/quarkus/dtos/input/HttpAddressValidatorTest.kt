package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpAddressValidatorTest {
    @Test
    fun `Given null, an https address, a blank, a non-address and an ftp address, Then only the first two pass`() {
        val values = listOf(null, "HTTPS://X.test/a", "", "not an address", "ftp://x.test/")

        val accepted = values.map { HttpAddressValidator().isValid(it, mockk()) }

        assertEquals(listOf(true, true, false, false, false), accepted)
    }
}
