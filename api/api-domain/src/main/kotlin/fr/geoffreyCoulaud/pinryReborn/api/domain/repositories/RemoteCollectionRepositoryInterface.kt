package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User

/** A board's deletion deletes its collections, through [BoardRepositoryInterface]. */
interface RemoteCollectionRepositoryInterface {
    fun saveRemoteCollection(collection: RemoteCollection): RemoteCollection

    /** The user's collection at exactly this address, whatever its board's state, or null. */
    fun findUserRemoteCollectionByUrl(user: User, url: String): RemoteCollection?
}
