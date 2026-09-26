package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ImageDownloadListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ImageDownloadOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PaginationOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinImageStateMapper.messageFor
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinImageStateMapper.toDto

object ImageDownloadDtoMapper {
    fun ImageDownload.toDto() = ImageDownloadOutputDto(
        pinId = pinId,
        sourceUrl = sourceUrl,
        status = status.toDto(),
        requestedAt = requestedAt,
        updatedAt = updatedAt,
        reasonCode = reasonCode?.toDto(),
        message = reasonCode?.let { messageFor(it) },
    )

    fun Page<ImageDownload>.toDto() = ImageDownloadListOutputDto(
        downloads = items.map { it.toDto() },
        pagination = PaginationOutputDto(
            previousCursor = previousCursor?.toDto(),
            nextCursor = nextCursor?.toDto(),
        ),
    )
}
