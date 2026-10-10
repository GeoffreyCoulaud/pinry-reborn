package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HttpUrlTest {
    private fun read(text: String): String? = HttpUrl.parse(text)?.toString()

    @Test
    fun `Given an address in mixed case with a default port, dot segments and an escape, Then it reads normalised`() {
        assertEquals("https://example.test/a/c?q=~#F", read("HTTPS://Example.TEST:443/a/./b/../c?q=%7e#F"))
    }

    @Test
    fun `Given a leading dot-dot segment, Then the address equals the one without it`() {
        assertEquals(HttpUrl.parse("https://x.test/a"), HttpUrl.parse("https://x.test/../a"))
    }

    @Test
    fun `Given trailing dot and dot-dot segments, Then each is removed with a trailing slash`() {
        assertEquals("https://x.test/a/", read("https://x.test/a/."))
        assertEquals("https://x.test/a/", read("https://x.test/a/b/.."))
    }

    @Test
    fun `Given an address with an empty path, Then its path reads as a slash`() {
        assertEquals("https://x.test/", read("https://x.test"))
    }

    @Test
    fun `Given two addresses differing by a trailing slash, Then they are two values`() {
        assertNotEquals(HttpUrl.parse("https://x.test/a"), HttpUrl.parse("https://x.test/a/"))
    }

    @Test
    fun `Given an escaped reserved character in lower case, Then it stays escaped in upper case`() {
        assertEquals("https://x.test/a%2Fb", read("https://x.test/a%2fb"))
    }

    @Test
    fun `Given a non-ASCII character, Then the address equals its UTF-8 escaped form`() {
        assertEquals(HttpUrl.parse("https://x.test/caf%C3%A9"), HttpUrl.parse("https://x.test/café"))
    }

    @Test
    fun `Given characters a browser escapes, Then they read escaped`() {
        assertEquals("https://x.test/a%20b%7Cc?q=%7Bx%7D", read("https://x.test/a b|c?q={x}"))
    }

    @Test
    fun `Given a percent sign not followed by two hexadecimal digits, Then it reads escaped`() {
        assertEquals("https://x.test/100%25", read("https://x.test/100%"))
        assertEquals("https://x.test/%25a", read("https://x.test/%a"))
        assertEquals("https://x.test/%25zz", read("https://x.test/%zz"))
    }

    @Test
    fun `Given a port other than the scheme's default, Then it is kept, and the default one is dropped`() {
        assertEquals("https://x.test:8443/", read("https://x.test:8443/"))
        assertEquals("http://x.test/", read("http://x.test:80/"))
    }

    @Test
    fun `Given credentials in an address, Then they are kept as written`() {
        assertEquals("https://user:pass@x.test/", read("https://user:pass@x.test/"))
    }

    @Test
    fun `Given a normalised text of the maximum length, Then it parses, and one character more is refused`() {
        val prefix = "https://x.test/"
        val longest = prefix + "a".repeat(HttpUrl.MAX_LENGTH - prefix.length)
        assertEquals(longest, read(longest))
        assertNull(HttpUrl.parse(longest + "a"))
    }

    @Test
    fun `Given a blank text, Then it is refused`() {
        assertNull(HttpUrl.parse(" "))
    }

    @Test
    fun `Given a relative address, Then it is refused`() {
        assertNull(HttpUrl.parse("/relative"))
    }

    @Test
    fun `Given a scheme other than http or https, Then it is refused`() {
        assertNull(HttpUrl.parse("ftp://x.test/"))
    }

    @Test
    fun `Given an address without a host, Then it is refused`() {
        assertNull(HttpUrl.parse("https:///path"))
    }

    @Test
    fun `Given a text the URI syntax refuses, Then it is refused`() {
        assertNull(HttpUrl.parse("https://[x.test/"))
    }

    @Test
    fun `Given a non-ASCII host, Then it is refused`() {
        assertNull(HttpUrl.parse("https://éx.test/"))
    }

    @Test
    fun `Given a host holding an underscore, Then it is refused`() {
        assertNull(HttpUrl.parse("https://a_b.example.test/x"))
    }
}
