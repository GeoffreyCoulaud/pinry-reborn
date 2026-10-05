package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import java.util.UUID

/** Pairs of pins whose media look alike (ADR 0051, decision 7). */
interface PinDuplicateRepositoryInterface {
    /** Delete [pinId]'s pairs the user has not rejected. */
    fun deletePending(pinId: UUID)

    /** Pair [pinId] with each of [otherPinIds] it is not paired with yet, rejected pairs included. */
    fun addMissing(pinId: UUID, otherPinIds: Collection<UUID>)

    /** Delete every pair one of whose pins is gone, recycled pins being kept; returns how many. */
    fun deleteOrphans(): Int

    /** The pins among [pinIds] that hold a pair the user has not rejected, both pins active, in one query. */
    fun findPinIdsWithPending(pinIds: Collection<UUID>): Set<UUID>
}
