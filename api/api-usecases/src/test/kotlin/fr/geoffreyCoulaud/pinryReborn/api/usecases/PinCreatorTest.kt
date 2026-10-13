package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.tagName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PinCreatorTest {
    private val pinRepository: PinRepositoryInterface = mockk()
    private val tagCreator: TagCreator = mockk()
    private val clockInstant = Instant.parse("2026-07-23T10:00:00Z")
    private val clock = mockk<Clock> { every { now() } returns clockInstant }
    private val useCase =
        PinCreator(
            tagCreator = tagCreator,
            pinRepository = pinRepository,
            clock = clock,
        )

    @Test
    fun `When creating a pin, then should succeed`() {
        // Given
        val user = aUser()
        val sourceUrl = HttpUrl.parse("https://example.com/article")
        val mediaUrl = HttpUrl.parse("https://example.com/media.jpeg")
        val description = "some description"
        val tags = listOf(tagName("blue"), tagName("landscape"), tagName("water"))
        tags.forEach { name ->
            every { tagCreator.findOrCreate(name, user) } returns
                Tag(id = randomUUID(), name = name, author = user, createdAt = TestTime.now)
        }
        every { pinRepository.savePin(any()) } answers { firstArg() }

        // When
        val pin =
            useCase.createPin(
                author = user,
                sourceContextUrl = sourceUrl,
                sourceMediaUrl = mediaUrl,
                description = description,
                tags = tags,
            )

        // Then
        assertEquals(user, pin.author)
        assertEquals(sourceUrl, pin.sourceContextUrl)
        assertEquals(mediaUrl, pin.sourceMediaUrl)
        assertEquals(description, pin.description)
        assertEquals(tags.toSet(), pin.tags.map { it.name }.toSet())
    }
}
