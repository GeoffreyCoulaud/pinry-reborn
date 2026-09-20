package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PinTagger(
    private val tagCreator: TagCreator,
) {
    /** The resolution half, split from the write, so [PinUpdater] runs it inside its own transaction. */
    fun resolveTags(tagNames: List<String>, user: User): List<Tag> =
        tagNames.map { tagCreator.findOrCreate(name = it, user = user) }
}
