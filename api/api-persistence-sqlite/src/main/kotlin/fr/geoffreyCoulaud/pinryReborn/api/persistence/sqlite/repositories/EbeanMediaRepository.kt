package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.PinQueries
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.withPinInAnyState
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class EbeanMediaRepository(
    private val persistor: Persistor,
    private val transactionRunner: TransactionRunner,
) : MediaRepositoryInterface {
    // The delete and the save are one unit; a caller's transaction is joined, not nested.
    override fun save(media: Media): Media = transactionRunner.inTransaction { saveWithin(media) }

    private fun saveWithin(media: Media): Media {
        QMediaModel().pinId.equalTo(media.pinId).delete()
        val model = media.toModel()
        persistor.save(model)
        return model.toDomain()
    }

    override fun findByPinId(pinId: UUID): Media? =
        QMediaModel().pinId.equalTo(pinId).findOne()?.toDomain()

    override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, Media> {
        if (pinIds.isEmpty()) return emptyMap()
        return QMediaModel().pinId.isIn(pinIds).findList().associate { it.pinId to it.toDomain() }
    }

    override fun deleteByPinId(pinId: UUID) {
        QMediaModel().pinId.equalTo(pinId).delete()
    }

    override fun findMissingMediaIds(candidates: Collection<UUID>): Set<UUID> {
        if (candidates.isEmpty()) return emptySet()
        val existing = QMediaModel().id.isIn(candidates).findIds<UUID>()
        return candidates.toSet() - existing.toSet()
    }

    override fun findNewestNotFingerprinted(version: Int): Media? =
        QMediaModel()
            .withPinInAnyState()
            .or()
            .fingerprintVersion.isNull
            .fingerprintVersion.lessThan(version)
            .endOr()
            .orderBy().createdAt.desc().id.desc()
            .setMaxRows(1)
            .findOne()
            ?.toDomain()

    override fun markFingerprinted(mediaId: UUID, version: Int) {
        QMediaModel().id.equalTo(mediaId).asUpdate().set("fingerprintVersion", version).update()
    }

    override fun findComparable(media: Media, candidates: Collection<UUID>, version: Int): List<Media> {
        val author = PinQueries.any().id.equalTo(media.pinId).select("author.id")
        return QMediaModel()
            .withPinInAnyState()
            .id.isIn(candidates)
            .pinId.notEqualTo(media.pinId)
            .animated.equalTo(media.animated)
            .fingerprintVersion.equalTo(version)
            .pin.author.id.isIn(author.query())
            .findList()
            .map { it.toDomain() }
    }
}
