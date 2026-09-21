package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User

interface TagRepositoryInterface {
    /**
     * Create or update a tag from the given domain data.
     */
    fun saveTag(tag: Tag): Tag

    /**
     * Find a tag with the given name for the specific user.
     * The fold is the index's own, ASCII case only, so two names differing outside A to Z are two names.
     */
    fun findUserTagByName(
        user: User,
        name: String,
    ): Tag?

    /**
     * Find all tags for a user
     */
    fun findAllTagsForUser(user: User): List<Tag>

    /**
     * Find at most [limit] of the user's tags whose name holds [query], those beginning with it
     * first. The fold is the index's own, ASCII case only.
     */
    fun findTagsForUserMatching(
        user: User,
        query: String,
        limit: Int,
    ): List<Tag>

    /**
     * Delete all tags for a user
     */
    fun deleteAllTagsForUser(user: User)
}
