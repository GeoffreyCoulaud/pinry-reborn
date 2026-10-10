package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaDownloadModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaDownloadModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaDownloadModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination.MediaDownloadModelSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination.ModelCursor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination.ModelPaginationHelper
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.withActivePin
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

@ApplicationScoped
// Splitting would fragment one adapter of one port across artificial classes, as UserDataExportRepository says.
@Suppress("TooManyFunctions")
class EbeanMediaDownloadRepository(private val persistor: Persistor) : MediaDownloadRepositoryInterface {
    // No explicit beginTransaction here: delete+save and the bulk CAS updates run under the ambient
    // transaction when TransactionRunner opened one (Ebean binds it to the thread), else auto-commit.
    override fun upsertPending(pinId: UUID, sourceUrl: HttpUrl, taskId: UUID, now: Instant): MediaDownload {
        QMediaDownloadModel().pinId.equalTo(pinId).delete()
        val model =
            MediaDownload(
                    pinId = pinId,
                    sourceUrl = sourceUrl,
                    status = DownloadStatus.PENDING,
                    reasonCode = null,
                    lastError = null,
                    taskId = taskId,
                    requestedAt = now,
                    updatedAt = now,
                )
                .toModel(id = UUID.randomUUID())
        persistor.save(model)
        return model.toDomain()
    }

    override fun findByPinId(pinId: UUID): MediaDownload? =
        QMediaDownloadModel().pinId.equalTo(pinId).findOne()?.toDomain()

    override fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, MediaDownload> {
        if (pinIds.isEmpty()) return emptyMap()
        return QMediaDownloadModel().pinId.isIn(pinIds).findList().associate { it.pinId to it.toDomain() }
    }

    // The recycled state is stated by the queries package and nowhere else, here through the
    // extension that navigates the association rather than a subquery this file would spell out.
    override fun findByAuthor(authorId: UUID, cursor: Cursor?, pageSize: Int): Page<MediaDownload> {
        // The pivot is read through the same ownership traversal as the page, so a cursor naming a
        // row the caller cannot see behaves exactly like one naming a row that is gone.
        val modelCursor =
            cursor
                ?.let {
                    QMediaDownloadModel()
                        .withActivePin()
                        .pin
                        .author
                        .id
                        .equalTo(authorId)
                        .id
                        .equalTo(it.pivotId)
                        .findOne()
                }
                ?.let { ModelCursor(pivot = it, direction = cursor.direction) }
        val modelPage =
            ModelPaginationHelper.getPage(
                cursor = modelCursor,
                pageSize = pageSize,
                baseQuery = QMediaDownloadModel().withActivePin().pin.author.id.equalTo(authorId),
                sortStrategy = MediaDownloadModelSortStrategy(),
            )
        return Page(
            items = modelPage.items.map { it.toDomain() },
            nextCursor = modelPage.nextCursor?.toDomain(),
            previousCursor = modelPage.previousCursor?.toDomain(),
        )
    }

    override fun findByAuthorAndPin(authorId: UUID, pinId: UUID): MediaDownload? =
        QMediaDownloadModel().withActivePin().pin.author.id.equalTo(authorId).pinId.equalTo(pinId).findOne()?.toDomain()

    override fun markFailed(pinId: UUID, reason: DownloadReason, now: Instant): Boolean =
        pendingRows(pinId)
            .asUpdate()
            .set("status", DownloadStatus.FAILED.name)
            .set("reasonCode", reason.name)
            .set("updatedAt", now)
            .update() > 0

    override fun recordLastError(pinId: UUID, lastError: String, now: Instant): Boolean =
        pendingRows(pinId).asUpdate().set("lastError", lastError).set("updatedAt", now).update() > 0

    override fun deleteIfPending(pinId: UUID): Int = pendingRows(pinId).delete()

    override fun deleteByPinId(pinId: UUID) {
        QMediaDownloadModel().pinId.equalTo(pinId).delete()
    }

    override fun findPending(): List<MediaDownload> =
        QMediaDownloadModel().status.equalTo(DownloadStatus.PENDING.name).findList().map { it.toDomain() }

    override fun deleteFailedBefore(cutoff: Instant): Int =
        QMediaDownloadModel().status.equalTo(DownloadStatus.FAILED.name).updatedAt.lessThan(cutoff).delete()

    private fun pendingRows(pinId: UUID) =
        QMediaDownloadModel().pinId.equalTo(pinId).status.equalTo(DownloadStatus.PENDING.name)
}
