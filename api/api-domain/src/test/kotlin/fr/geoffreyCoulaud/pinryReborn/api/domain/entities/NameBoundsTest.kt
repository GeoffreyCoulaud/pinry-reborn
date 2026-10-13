package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NameBoundsTest {
    @Test
    fun `Given an empty text, Then it is refused`() {
        assertFalse(NameBounds.accepts(""))
    }

    @Test
    fun `Given a text of one space, Then it is refused`() {
        assertFalse(NameBounds.accepts(" "))
    }

    @Test
    fun `Given a text of one em space, Then it is refused`() {
        assertFalse(NameBounds.accepts(" "))
    }

    @Test
    fun `Given a text of 200 characters, Then it is accepted, and one of 201 is refused`() {
        assertTrue(NameBounds.accepts("a".repeat(200)))
        assertFalse(NameBounds.accepts("a".repeat(201)))
    }
}
