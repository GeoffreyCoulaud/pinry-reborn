package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.SearchEmptyQueryError
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class TagSearcher(
    private val tagRepository: TagRepositoryInterface,
) {
    /**
     * Find at most [limit] of the user's tags matching [query], in the order the store serves them.
     * @throws SearchEmptyQueryError when the query is blank, an absent one included.
     */
    fun searchTags(
        user: User,
        query: String,
        limit: Int,
    ): List<Tag> {
        if (query.isBlank()) {
            throw SearchEmptyQueryError()
        }

        return tagRepository.findTagsForUserMatching(user = user, query = query, limit = limit)
    }
}
