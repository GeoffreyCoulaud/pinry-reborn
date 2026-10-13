package fr.geoffreyCoulaud.pinryReborn.api.domain

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.BoardName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.CollectionName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.TagName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Username
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

/** The domain's values as tests build them, each through its factory. */
object DomainFixtures {
    fun tagName(text: String): TagName = checkNotNull(TagName.parse(text)) { "Not a tag name: $text" }

    fun boardName(text: String): BoardName = checkNotNull(BoardName.parse(text)) { "Not a board name: $text" }

    fun collectionName(text: String): CollectionName =
        checkNotNull(CollectionName.parse(text)) { "Not a collection name: $text" }

    fun username(text: String): Username = checkNotNull(Username.parse(text)) { "Not an account name: $text" }

    fun aUser(
        name: String = "user-${randomUUID()}",
        id: UUID = randomUUID(),
        createdAt: Instant = TestTime.now,
        softDeletedAt: Instant? = null,
    ): User = User(id = id, name = username(name), createdAt = createdAt, softDeletedAt = softDeletedAt)
}
