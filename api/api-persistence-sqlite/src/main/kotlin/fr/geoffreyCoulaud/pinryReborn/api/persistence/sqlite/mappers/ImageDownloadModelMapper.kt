package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.ImageDownloadModel
import java.util.UUID

object ImageDownloadModelMapper {
    /** [id] is the row's surrogate key, which the domain does not carry: the pin identifies a download. */
    fun ImageDownload.toModel(id: UUID) = ImageDownloadModel(
        id = id, pinId = pinId, sourceUrl = sourceUrl, status = status.name, reasonCode = reasonCode?.name,
        lastError = lastError, taskId = taskId, requestedAt = requestedAt, updatedAt = updatedAt,
    )

    fun ImageDownloadModel.toDomain() = ImageDownload(
        pinId = pinId, sourceUrl = sourceUrl, status = DownloadStatus.valueOf(status),
        reasonCode = reasonCode?.let { DownloadReason.valueOf(it) }, lastError = lastError,
        taskId = taskId, requestedAt = requestedAt, updatedAt = updatedAt,
    )
}
