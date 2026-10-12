package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User

interface TagRepositoryInterface {
    /** Create or update a tag from the given domain data. */
    fun saveTag(tag: Tag): Tag

    /**
     * Find a tag with the given name for the specific user. The fold is the store's own, case and width in every
     * script.
     */
    fun findUserTagByName(
        user: User,
        name: String,
    ): Tag?

    /** Find all tags for a user */
    fun findAllTagsForUser(user: User): List<Tag>

    /** Find at most [limit] of the user's tags whose name holds [query], those beginning with it first, case folded. */
    fun findTagsForUserMatching(
        user: User,
        query: String,
        limit: Int,
    ): List<Tag>
}
