package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CollectionNameTest {
    @Test
    fun `Given two collection names differing by case, Then they are not equal`() {
        assertNotEquals(CollectionName.parse("Feed"), CollectionName.parse("feed"))
    }

    @Test
    fun `Given a collection name in upper case, Then it reads back as written`() {
        assertEquals("FEED", CollectionName.parse("FEED")?.text)
        assertEquals("FEED", CollectionName.parse("FEED").toString())
    }

    @Test
    fun `Given a collection name of one space, Then it is refused`() {
        assertNull(CollectionName.parse(" "))
    }

    @Test
    fun `Given a collection name at the length bound, Then it parses, and one character more is refused`() {
        assertNotNull(CollectionName.parse("a".repeat(NameBounds.MAX_LENGTH)))
        assertNull(CollectionName.parse("a".repeat(NameBounds.MAX_LENGTH + 1)))
    }

    @Test
    fun `Given a collection name, Then the board named after it reads as written`() {
        val collection = checkNotNull(CollectionName.parse("Feed"))

        assertEquals("Feed", BoardName.namedAfter(collection).text)
    }
}
