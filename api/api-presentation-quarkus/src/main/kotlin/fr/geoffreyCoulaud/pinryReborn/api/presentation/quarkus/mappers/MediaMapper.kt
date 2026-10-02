package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.MediaOutputDto

object MediaMapper {
    fun Media.toDto() = MediaOutputDto(
        id = id,
        pinId = pinId,
        mimeType = mimeType,
        width = width,
        height = height,
        byteSize = byteSize,
        url = "/api/v1/pins/$pinId/media",
    )
}
