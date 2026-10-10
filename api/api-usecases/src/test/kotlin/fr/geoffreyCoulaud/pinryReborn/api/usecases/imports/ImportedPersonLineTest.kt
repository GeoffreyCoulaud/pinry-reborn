package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImportedPersonLineTest {
    @Test
    fun `Given an archived name the line's check should have refused, Then reading the person throws`() {
        assertThrows<IllegalStateException> { ImportedPersonLine("ada", " ", emptyList(), Instant.EPOCH).toReference() }
    }
}
