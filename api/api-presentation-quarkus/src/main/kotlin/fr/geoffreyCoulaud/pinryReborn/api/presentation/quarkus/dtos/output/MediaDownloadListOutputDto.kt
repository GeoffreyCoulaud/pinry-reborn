package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

data class MediaDownloadListOutputDto(
    val downloads: List<MediaDownloadOutputDto>,
    val pagination: PaginationOutputDto,
)
