package fr.geoffreyCoulaud.pinryReborn.api.domain

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.TagName

/** The domain's values as tests build them, each through its factory. */
object DomainFixtures {
    fun tagName(text: String): TagName = checkNotNull(TagName.parse(text)) { "Not a tag name: $text" }
}
