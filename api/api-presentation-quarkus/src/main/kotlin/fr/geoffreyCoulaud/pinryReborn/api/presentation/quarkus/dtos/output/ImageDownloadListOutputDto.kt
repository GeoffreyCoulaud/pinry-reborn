package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

data class ImageDownloadListOutputDto(
    val downloads: List<ImageDownloadOutputDto>,
    val pagination: PaginationOutputDto,
)
