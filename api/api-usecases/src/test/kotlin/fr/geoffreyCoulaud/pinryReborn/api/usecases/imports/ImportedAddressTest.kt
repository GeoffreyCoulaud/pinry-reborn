package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImportedAddressTest {
    @Test
    fun `Given an archived address the line's check accepted, Then it reads normalised`() {
        assertEquals("https://x.test/a", ImportedAddress.read("HTTPS://X.test/a").toString())
    }

    @Test
    fun `Given an archived text the line's check should have refused, Then reading it throws`() {
        assertThrows<IllegalStateException> { ImportedAddress.read("ftp://x.test/") }
    }
}
