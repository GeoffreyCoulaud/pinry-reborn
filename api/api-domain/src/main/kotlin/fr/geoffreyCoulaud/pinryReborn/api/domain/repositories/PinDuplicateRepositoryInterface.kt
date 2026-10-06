package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import java.time.Instant
import java.util.UUID

/** Pairs of pins whose media look alike (ADR 0051, decision 7). */
interface PinDuplicateRepositoryInterface {
    /** Delete [pinId]'s pairs the user has not rejected. */
    fun deletePending(pinId: UUID)

    /** Pair [pinId] with each of [otherPinIds] it is not paired with yet, rejected pairs included. */
    fun addMissing(pinId: UUID, otherPinIds: Collection<UUID>)

    /** Delete every pair one of whose pins is gone, and every pending one whose pin has no media; returns how many. */
    fun deleteOrphans(): Int

    /** [pinId]'s shown pairs, both pins active, keyed by the other pin's id; true when the user rejected it. */
    fun findShownFor(pinId: UUID): Map<UUID, Boolean>

    /** The pins among [pinIds] that hold a pair the user has not rejected, both pins active, in one query. */
    fun findPinIdsWithPending(pinIds: Collection<UUID>): Set<UUID>

    /** Stamp the shown pair of [pinId] and [otherPinId], if there is one, with [rejectedAt]. */
    fun setRejected(pinId: UUID, otherPinId: UUID, rejectedAt: Instant)
}
