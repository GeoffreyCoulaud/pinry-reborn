package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

data class TagSearchResultOutputDto(
    val tag: TagOutputDto,
)

data class TagSearchOutputDto(
    val results: List<TagSearchResultOutputDto>,
)
