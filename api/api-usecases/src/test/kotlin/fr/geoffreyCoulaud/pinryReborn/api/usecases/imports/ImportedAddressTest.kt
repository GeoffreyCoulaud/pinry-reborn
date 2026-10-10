package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImportedAddressTest {
    @Test
    fun `Given an archived text the line's check should have refused, Then reading it throws`() {
        assertThrows<IllegalStateException> { ImportedAddress.read("ftp://x.test/") }
    }
}
