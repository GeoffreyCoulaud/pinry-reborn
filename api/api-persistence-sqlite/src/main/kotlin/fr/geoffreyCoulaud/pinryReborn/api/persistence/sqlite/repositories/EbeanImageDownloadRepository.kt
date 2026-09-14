package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.ImageDownloadModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.ImageDownloadModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QImageDownloadModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.withActivePin
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("TooManyFunctions")
class EbeanImageDownloadRepository(
    private val persistor: Persistor,
) : ImageDownloadRepositoryInterface {
    // No explicit beginTransaction here: delete+save and the bulk CAS updates run under the ambient
    // transaction when TransactionRunner opened one (Ebean binds it to the thread), else auto-commit.
    override fun upsertPending(pinId: UUID, sourceUrl: String, taskId: UUID, now: Instant): ImageDownload {
        QImageDownloadModel().pinId.equalTo(pinId).delete()
        val model = ImageDownload(
            pinId = pinId, sourceUrl = sourceUrl, status = DownloadStatus.PENDING, reasonCode = null,
            lastError = null, taskId = taskId, requestedAt = now, updatedAt = now,
        ).toModel(id = UUID.randomUUID())
        persistor.save(model)
        return model.toDomain()
    }

    override fun findByPinId(pinId: UUID): ImageDownload? =
        QImageDownloadModel().pinId.equalTo(pinId).findOne()?.toDomain()

    override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, ImageDownload> {
        if (pinIds.isEmpty()) return emptyMap()
        return QImageDownloadModel().pinId.isIn(pinIds).findList().associate { it.pinId to it.toDomain() }
    }

    // The recycled state is stated by the queries package and nowhere else, here through the
    // extension that navigates the association rather than a subquery this file would spell out.
    override fun findByAuthor(authorId: UUID): List<ImageDownload> =
        QImageDownloadModel()
            .withActivePin()
            .pin.author.id.equalTo(authorId)
            .orderBy().requestedAt.desc()
            .findList()
            .map { it.toDomain() }

    override fun findByAuthorAndPin(authorId: UUID, pinId: UUID): ImageDownload? =
        QImageDownloadModel()
            .withActivePin()
            .pin.author.id.equalTo(authorId)
            .pinId.equalTo(pinId)
            .findOne()
            ?.toDomain()

    override fun markFailed(pinId: UUID, reason: DownloadReason, now: Instant): Boolean =
        pendingRows(pinId)
            .asUpdate()
            .set("status", DownloadStatus.FAILED.name)
            .set("reasonCode", reason.name)
            .set("updatedAt", now)
            .update() > 0

    override fun recordLastError(pinId: UUID, lastError: String, now: Instant): Boolean =
        pendingRows(pinId)
            .asUpdate()
            .set("lastError", lastError)
            .set("updatedAt", now)
            .update() > 0

    override fun deleteIfPending(pinId: UUID): Int = pendingRows(pinId).delete()

    override fun deleteByPinId(pinId: UUID) {
        QImageDownloadModel().pinId.equalTo(pinId).delete()
    }

    override fun findPending(): List<ImageDownload> =
        QImageDownloadModel()
            .status.equalTo(DownloadStatus.PENDING.name)
            .findList()
            .map { it.toDomain() }

    override fun deleteFailedBefore(cutoff: Instant): Int =
        QImageDownloadModel()
            .status.equalTo(DownloadStatus.FAILED.name)
            .updatedAt.lessThan(cutoff)
            .delete()

    private fun pendingRows(pinId: UUID) =
        QImageDownloadModel().pinId.equalTo(pinId).status.equalTo(DownloadStatus.PENDING.name)
}
