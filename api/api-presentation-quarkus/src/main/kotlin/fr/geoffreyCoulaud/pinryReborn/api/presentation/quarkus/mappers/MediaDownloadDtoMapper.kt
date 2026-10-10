package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.MediaDownloadListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.MediaDownloadOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PaginationOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMediaStateMapper.messageFor
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMediaStateMapper.toDto

object MediaDownloadDtoMapper {
    fun MediaDownload.toDto() =
        MediaDownloadOutputDto(
            pinId = pinId,
            sourceUrl = sourceUrl.toString(),
            status = status.toDto(),
            requestedAt = requestedAt,
            updatedAt = updatedAt,
            reasonCode = reasonCode?.toDto(),
            message = reasonCode?.let { messageFor(it) },
        )

    fun Page<MediaDownload>.toDto() =
        MediaDownloadListOutputDto(
            downloads = items.map { it.toDto() },
            pagination =
                PaginationOutputDto(
                    previousCursor = previousCursor?.toDto(),
                    nextCursor = nextCursor?.toDto(),
                ),
        )
}
