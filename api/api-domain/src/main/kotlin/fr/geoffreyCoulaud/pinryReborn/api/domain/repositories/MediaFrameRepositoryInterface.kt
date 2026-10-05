package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import java.util.UUID

interface MediaFrameRepositoryInterface {
    /** Store [hashes] as frames of [mediaId]. */
    fun save(mediaId: UUID, hashes: Collection<PdqHash>)

    /** Every stored frame within `PdqHasher.MATCH_DISTANCE` bits of [hash], found through the band indexes. */
    fun findNear(hash: PdqHash): List<MediaFrame>

    /** Every frame of [mediaIds]. */
    fun findByMediaIds(mediaIds: Collection<UUID>): List<MediaFrame>

    /** Delete every frame of [mediaId]. */
    fun deleteByMediaId(mediaId: UUID)
}
