package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.SearchResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.TagSearchOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.TagSearchResultOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.TagMapper.toDto

object SearchResultMapper {
    fun List<SearchResult<Tag>>.toTagSearchDto() = TagSearchOutputDto(
        results = this.map { result ->
            TagSearchResultOutputDto(
                tag = result.item.toDto(),
                score = result.score,
            )
        }
    )
}
