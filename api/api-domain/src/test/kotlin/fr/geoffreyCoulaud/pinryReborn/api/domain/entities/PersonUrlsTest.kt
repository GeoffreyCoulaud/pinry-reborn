package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonUrlsTest {
    @Test
    fun `Given addresses out of order and repeated, Then they are stored sorted, distinct and line-feed joined`() {
        // When
        val urls = PersonUrls.of(listOf("https://b.test/", "https://a.test/", "https://b.test/"))

        // Then
        assertEquals(listOf("https://a.test/", "https://b.test/"), urls.values)
        assertEquals("https://a.test/\nhttps://b.test/", urls.joined)
    }

    @Test
    fun `Given no address, Then the canonical form is empty`() {
        // When
        val urls = PersonUrls.of(emptyList())

        // Then
        assertEquals("", urls.joined)
    }

    @Test
    fun `Given the same addresses in two orders, Then both give one canonical form`() {
        // When
        val first = PersonUrls.of(listOf("https://a.test/", "https://b.test/"))
        val second = PersonUrls.of(listOf("https://b.test/", "https://a.test/"))

        // Then
        assertEquals(first, second)
    }

    @Test
    fun `Given an address with and without its trailing slash, Then they stay two addresses`() {
        // When
        val urls = PersonUrls.of(listOf("https://x.test/a", "https://x.test/a/"))

        // Then: no normalisation beyond the canonical order
        assertEquals(listOf("https://x.test/a", "https://x.test/a/"), urls.values)
    }

    @Test
    fun `Given a stored form, Then parsing it gives back the addresses`() {
        // Given
        val urls = PersonUrls.of(listOf("https://b.test/", "https://a.test/"))

        // When / Then
        assertEquals(urls, PersonUrls.parse(urls.joined))
    }

    @Test
    fun `Given the empty stored form, Then parsing it gives no address`() {
        // When / Then
        assertEquals(PersonUrls.of(emptyList()), PersonUrls.parse(""))
    }
}
