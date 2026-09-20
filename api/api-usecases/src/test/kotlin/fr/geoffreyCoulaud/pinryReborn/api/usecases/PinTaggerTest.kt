package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class PinTaggerTest {
    private val tagCreator = mockk<TagCreator>()
    private val useCase = PinTagger(tagCreator = tagCreator)

    @Test
    fun `Given several names, Then resolveTags answers the tag each one resolves to, in order`() {
        // Given
        val user = User(id = randomUUID(), name = "John Doe", createdAt = TestTime.now)
        val first = Tag(id = randomUUID(), name = "nature", author = user, createdAt = TestTime.now)
        val second = Tag(id = randomUUID(), name = "mountain", author = user, createdAt = TestTime.now)
        every { tagCreator.findOrCreate(name = "nature", user = user) } returns first
        every { tagCreator.findOrCreate(name = "mountain", user = user) } returns second

        // When
        val result = useCase.resolveTags(tagNames = listOf("nature", "mountain"), user = user)

        // Then
        assertEquals(listOf(first, second), result)
    }
}
