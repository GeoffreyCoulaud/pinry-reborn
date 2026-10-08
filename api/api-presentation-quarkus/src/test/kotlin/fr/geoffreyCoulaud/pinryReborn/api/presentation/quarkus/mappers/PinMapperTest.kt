package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinMediaStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaStatus
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PinMapperTest {
    private fun createPin(): Pin =
        Pin(
            id = randomUUID(),
            author = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now),
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = createRandomString(),
            tags = emptyList(),
            boards = emptyList(),
            createdAt = TestTime.now,
            updatedAt = TestTime.now,
        )

    @Test
    fun `Given a page with no previous and no next cursor, Then toDto maps both cursors to null`() {
        // Given
        val page = Page<Pin>(items = listOf(createPin()), previousCursor = null, nextCursor = null)

        // When
        val result = page.toDto(emptyMap(), emptySet())

        // Then
        assertNull(result.pagination.previousCursor)
        assertNull(result.pagination.nextCursor)
    }

    @Test
    fun `Given a page with a previous and a next cursor, Then toDto maps both cursors`() {
        // Given
        val previousCursor = Cursor(pivotId = randomUUID(), direction = CursorDirection.BACKWARD)
        val nextCursor = Cursor(pivotId = randomUUID(), direction = CursorDirection.FORWARD)
        val page = Page<Pin>(items = listOf(createPin()), previousCursor = previousCursor, nextCursor = nextCursor)

        // When
        val result = page.toDto(emptyMap(), emptySet())

        // Then
        assertNotNull(result.pagination.previousCursor)
        assertNotNull(result.pagination.nextCursor)
    }

    @Test
    fun `Given a pin, Then toDto carries its creation instant`() {
        // Given
        val pin = createPin().copy(createdAt = Instant.parse("2026-01-02T03:04:05Z"))

        // When
        val result = pin.toDto(emptyMap(), emptySet())

        // Then
        assertEquals(pin.createdAt, result.createdAt)
    }

    @Test
    fun `Given a pin whose image is ready, Then toDto carries its dimensions and a relative url`() {
        // Given
        val pin = createPin()
        val media =
            Media.StillImage(
                id = randomUUID(),
                pinId = pin.id,
                mimeType = "image/png",
                width = 800,
                height = 600,
                byteSize = 1024,
                contentHash = "h",
                storageKey = "originals/x/y/z.png",
                createdAt = TestTime.now,
            )
        val states = mapOf(pin.id to PinMediaState(PinMediaStatus.READY, media, null, null))

        // When
        val result = pin.toDto(states, emptySet())

        // Then
        assertEquals(PinMediaStatusDto.READY, result.media?.status)
        assertEquals("/api/v1/pins/${pin.id}/media", result.media?.url)
        assertEquals(800, result.media?.width)
        assertEquals(600, result.media?.height)
    }

    @Test
    fun `Given a pin whose download is pending, Then toDto carries PENDING and no dimensions`() {
        // Given
        val pin = createPin()
        val states = mapOf(pin.id to PinMediaState(PinMediaStatus.PENDING, null, null, null))

        // When
        val result = pin.toDto(states, emptySet())

        // Then
        assertEquals(PinMediaStatusDto.PENDING, result.media?.status)
        assertNull(result.media?.url)
        assertNull(result.media?.width)
        assertNull(result.media?.height)
    }

    @Test
    fun `Given a pin crediting people, Then toDto carries each by its name and addresses, and the instant`() {
        // Given
        val pin = createPin()
        val alice = person(pin, "Alice", listOf("https://alice.test"))
        val bob = person(pin, "Bob", emptyList())
        val credited =
            pin.copy(publisher = alice, creators = listOf(bob), publishedAt = Instant.parse("2019-05-01T12:00:00Z"))

        // When
        val result = credited.toDto(emptyMap(), emptySet())

        // Then
        assertEquals(PersonOutputDto(name = "Alice", urls = listOf("https://alice.test")), result.publisher)
        assertEquals(listOf(PersonOutputDto(name = "Bob", urls = emptyList())), result.creators)
        assertEquals(credited.publishedAt, result.publishedAt)
    }

    @Test
    fun `Given a pin crediting no one, Then toDto carries no publisher, no creator and no instant`() {
        // When
        val result = createPin().toDto(emptyMap(), emptySet())

        // Then
        assertNull(result.publisher)
        assertEquals(emptyList<PersonOutputDto>(), result.creators)
        assertNull(result.publishedAt)
    }

    private fun person(pin: Pin, name: String, urls: List<String>) =
        Person(
            id = randomUUID(),
            author = pin.author,
            name = name,
            urls = urls,
            createdAt = TestTime.now,
        )

    @Test
    fun `Given a pin absent from the resolved states, Then toDto carries no image at all`() {
        // Given
        val pin = createPin()

        // When
        val result = pin.toDto(emptyMap(), emptySet())

        // Then
        assertNull(result.media)
    }
}
