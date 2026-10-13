package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PersonNameTest {
    @Test
    fun `Given two names of the same text, Then they are equal with equal hash codes`() {
        val first = PersonName.parse("Alice")
        val second = PersonName.parse("Alice")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `Given two names differing by an ASCII letter's case, Then they are not equal`() {
        assertNotEquals(PersonName.parse("Alice"), PersonName.parse("alice"))
    }

    @Test
    fun `Given two names differing by a non-ASCII letter's case, Then they are not equal`() {
        assertNotEquals(PersonName.parse("Élodie"), PersonName.parse("élodie"))
    }

    @Test
    fun `Given a name in upper case, Then it reads back as written`() {
        assertEquals("ALICE", PersonName.parse("ALICE")?.text)
        assertEquals("ALICE", PersonName.parse("ALICE").toString())
    }

    @Test
    fun `Given a name and a text of the same characters, Then they are not equal`() {
        assertFalse(checkNotNull(PersonName.parse("Alice")).equals("Alice"))
    }

    @Test
    fun `Given a blank name, Then it is refused`() {
        assertNull(PersonName.parse(" "))
    }

    @Test
    fun `Given a name at the length bound, Then it parses, and one character more is refused`() {
        assertNotNull(PersonName.parse("a".repeat(NameBounds.MAX_LENGTH)))
        assertNull(PersonName.parse("a".repeat(NameBounds.MAX_LENGTH + 1)))
    }
}
