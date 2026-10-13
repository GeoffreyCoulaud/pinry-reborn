package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UsernameTest {
    @Test
    fun `Given a name in the alphabet with a dot and capitals, Then it reads back as written`() {
        assertEquals("Alice.B", Username.parse("Alice.B")?.text)
        assertEquals("Alice.B", Username.parse("Alice.B").toString())
    }

    @Test
    fun `Given two account names differing by case, Then they are not equal`() {
        assertNotEquals(Username.parse("Alice"), Username.parse("alice"))
    }

    @Test
    fun `Given an account name of three characters, Then it parses, and one of two is refused`() {
        assertNotNull(Username.parse("abc"))
        assertNull(Username.parse("ab"))
    }

    @Test
    fun `Given an account name holding a space, Then it is refused`() {
        assertNull(Username.parse("a b"))
    }

    @Test
    fun `Given an account name with a fullwidth letter, Then it is refused`() {
        assertNull(Username.parse("ａlice"))
    }

    @Test
    fun `Given an account name at the length bound, Then it parses, and one character more is refused`() {
        assertNotNull(Username.parse("a".repeat(Username.MAX_LENGTH)))
        assertNull(Username.parse("a".repeat(Username.MAX_LENGTH + 1)))
    }
}
