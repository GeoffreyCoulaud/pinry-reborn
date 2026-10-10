package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImportedPersonTest {
    @Test
    fun `Given an archived name the line's check should have refused, Then reading the person throws`() {
        assertThrows<IllegalStateException> { ImportedPerson(" ", emptyList()).toReference() }
    }
}
