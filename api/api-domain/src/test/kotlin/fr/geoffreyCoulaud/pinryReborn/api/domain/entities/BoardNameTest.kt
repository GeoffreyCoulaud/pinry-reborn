package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BoardNameTest {
    @Test
    fun `Given two board names differing by case, Then they are not equal`() {
        assertNotEquals(BoardName.parse("Holidays"), BoardName.parse("holidays"))
    }

    @Test
    fun `Given a board name in upper case, Then it reads back as written`() {
        assertEquals("HOLIDAYS", BoardName.parse("HOLIDAYS")?.text)
        assertEquals("HOLIDAYS", BoardName.parse("HOLIDAYS").toString())
    }

    @Test
    fun `Given a board name of one em space, Then it is refused`() {
        assertNull(BoardName.parse(" "))
    }

    @Test
    fun `Given a board name at the length bound, Then it parses, and one character more is refused`() {
        assertNotNull(BoardName.parse("a".repeat(NameBounds.MAX_LENGTH)))
        assertNull(BoardName.parse("a".repeat(NameBounds.MAX_LENGTH + 1)))
    }
}
