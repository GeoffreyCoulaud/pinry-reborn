package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import java.util.UUID

interface MediaRepositoryInterface {
    /**
     * Create or replace the image for a pin, in a single transaction.
     */
    fun save(media: Media): Media

    /**
     * Find the image attached to a pin, if any.
     */
    fun findByPinId(pinId: UUID): Media?

    /**
     * The images attached to [pinIds], keyed by pin id; a pin with no image is absent from the map.
     * Backed by one `IN (...)` lookup, so the call is bounded by the size of [pinIds] (a page).
     */
    fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, Media>

    /**
     * Delete the image attached to a pin, if any.
     */
    fun deleteByPinId(pinId: UUID)

    /**
     * Return the candidate ids that have no image row, i.e. the orphans the garbage collection
     * sweep should reclaim. Backed by a primary-key `IN (...)` lookup, so the
     * call is bounded by the size of [candidates] (the orphan sweep chunks it).
     */
    fun findMissingMediaIds(candidates: Collection<UUID>): Set<UUID>
}
