package fr.geoffreyCoulaud.pinryReborn.api.domain

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.BoardName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.CollectionName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.TagName

/** The domain's values as tests build them, each through its factory. */
object DomainFixtures {
    fun tagName(text: String): TagName = checkNotNull(TagName.parse(text)) { "Not a tag name: $text" }

    fun boardName(text: String): BoardName = checkNotNull(BoardName.parse(text)) { "Not a board name: $text" }

    fun collectionName(text: String): CollectionName =
        checkNotNull(CollectionName.parse(text)) { "Not a collection name: $text" }
}
