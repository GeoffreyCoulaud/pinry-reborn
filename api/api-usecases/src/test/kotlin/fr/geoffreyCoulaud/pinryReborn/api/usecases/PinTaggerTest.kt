package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.tagName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PinTaggerTest {
    private val tagCreator = mockk<TagCreator>()
    private val useCase = PinTagger(tagCreator = tagCreator)

    @Test
    fun `Given several names, Then resolveTags answers the tag each one resolves to, in order`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val first = Tag(id = randomUUID(), name = tagName("nature"), author = user, createdAt = TestTime.now)
        val second = Tag(id = randomUUID(), name = tagName("mountain"), author = user, createdAt = TestTime.now)
        every { tagCreator.findOrCreate(name = tagName("nature"), user = user) } returns first
        every { tagCreator.findOrCreate(name = tagName("mountain"), user = user) } returns second

        // When
        val result = useCase.resolveTags(tagNames = listOf(tagName("nature"), tagName("mountain")), user = user)

        // Then
        assertEquals(listOf(first, second), result)
    }
}
