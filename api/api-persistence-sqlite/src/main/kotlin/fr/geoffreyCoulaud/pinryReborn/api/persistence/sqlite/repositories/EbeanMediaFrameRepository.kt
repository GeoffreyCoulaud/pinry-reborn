package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHasher
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaFrameRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.MediaFrameModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaFrameModel
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.UUID.randomUUID

@ApplicationScoped
class EbeanMediaFrameRepository(
    private val persistor: Persistor,
) : MediaFrameRepositoryInterface {
    override fun save(mediaId: UUID, hashes: Collection<PdqHash>) {
        hashes.forEach { hash ->
            val word = hash.words.iterator()
            persistor.save(MediaFrameModel(randomUUID(), mediaId, word.next(), word.next(), word.next(), word.next()))
        }
    }

    override fun findNear(hash: PdqHash): List<MediaFrame> =
        nearQuery(hash)
            .findList()
            .map { MediaFrame(it.mediaId, listOf(it.hash0, it.hash1, it.hash2, it.hash3)) }
            .filter { distance(it.words, hash.words) <= PdqHasher.MATCH_DISTANCE }

    private fun distance(stored: List<Long>, probe: List<Long>) =
        stored.zip(probe).sumOf { (mine, theirs) -> java.lang.Long.bitCount(mine xor theirs) }

    /**
     * Every frame sharing a band with [hash] within one bit: one `in` per band, each its own raw expression,
     * because Ebean expands `?1` by substring and would also rewrite a `?10`. `internal` so its tests read this SQL.
     */
    internal fun nearQuery(hash: PdqHash): QMediaFrameModel {
        val band = FrameHashBands.valuesNear(hash).iterator()
        return QMediaFrameModel()
            .or()
            .raw("((hash_0 >> 48) & 65535) in (?1)", band.next())
            .raw("((hash_0 >> 32) & 65535) in (?1)", band.next())
            .raw("((hash_0 >> 16) & 65535) in (?1)", band.next())
            .raw("((hash_0 >> 0) & 65535) in (?1)", band.next())
            .raw("((hash_1 >> 48) & 65535) in (?1)", band.next())
            .raw("((hash_1 >> 32) & 65535) in (?1)", band.next())
            .raw("((hash_1 >> 16) & 65535) in (?1)", band.next())
            .raw("((hash_1 >> 0) & 65535) in (?1)", band.next())
            .raw("((hash_2 >> 48) & 65535) in (?1)", band.next())
            .raw("((hash_2 >> 32) & 65535) in (?1)", band.next())
            .raw("((hash_2 >> 16) & 65535) in (?1)", band.next())
            .raw("((hash_2 >> 0) & 65535) in (?1)", band.next())
            .raw("((hash_3 >> 48) & 65535) in (?1)", band.next())
            .raw("((hash_3 >> 32) & 65535) in (?1)", band.next())
            .raw("((hash_3 >> 16) & 65535) in (?1)", band.next())
            .raw("((hash_3 >> 0) & 65535) in (?1)", band.next())
            .endOr()
    }
}
