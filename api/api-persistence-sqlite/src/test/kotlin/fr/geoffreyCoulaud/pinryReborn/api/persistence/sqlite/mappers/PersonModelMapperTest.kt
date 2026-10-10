package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PersonModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.UserModel
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PersonModelMapperTest {
    @Test
    fun `Given a stored name the factory refuses, Then the mapper throws rather than invent a name`() {
        val createdAt = Instant.parse("2026-10-10T00:00:00Z")
        val author = UserModel(id = randomUUID(), name = "author", createdAt = createdAt)
        val model = PersonModel(id = randomUUID(), author = author, name = " ", urls = "[]", createdAt = createdAt)

        assertThrows<IllegalStateException> { model.toDomain() }
    }
}
