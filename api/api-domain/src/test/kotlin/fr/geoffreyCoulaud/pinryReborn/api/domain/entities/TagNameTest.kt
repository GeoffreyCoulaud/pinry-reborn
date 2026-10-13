package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TagNameTest {
    @Test
    fun `Given two tag names differing by case, Then they are not equal`() {
        assertNotEquals(TagName.parse("Landscape"), TagName.parse("landscape"))
    }

    @Test
    fun `Given a tag name in upper case, Then it reads back as written`() {
        assertEquals("LANDSCAPE", TagName.parse("LANDSCAPE")?.text)
        assertEquals("LANDSCAPE", TagName.parse("LANDSCAPE").toString())
    }

    @Test
    fun `Given a tag name of one em space, Then it is refused`() {
        assertNull(TagName.parse(" "))
    }

    @Test
    fun `Given a tag name at the length bound, Then it parses, and one character more is refused`() {
        assertNotNull(TagName.parse("a".repeat(NameBounds.MAX_LENGTH)))
        assertNull(TagName.parse("a".repeat(NameBounds.MAX_LENGTH + 1)))
    }
}
