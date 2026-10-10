package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import java.util.UUID

/** A board's deletion deletes its collections, through [BoardRepositoryInterface]. */
interface RemoteCollectionRepositoryInterface {
    fun saveRemoteCollection(collection: RemoteCollection): RemoteCollection

    /** The user's collection at this address, whatever its board's state, or null. */
    fun findUserRemoteCollectionByUrl(user: User, url: HttpUrl): RemoteCollection?

    /** The board's collections, sorted by name with its case folded. */
    fun findRemoteCollectionsForBoard(boardId: UUID): List<RemoteCollection>

    /** Every collection of the user, whatever its board's state. */
    fun findAllRemoteCollectionsForUser(user: User): List<RemoteCollection>
}
