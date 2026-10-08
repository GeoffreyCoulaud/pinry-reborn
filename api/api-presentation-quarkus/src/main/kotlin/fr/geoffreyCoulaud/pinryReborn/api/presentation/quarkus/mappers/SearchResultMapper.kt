package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonSearchOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonSearchResultOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.TagSearchOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.TagSearchResultOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PersonMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.TagMapper.toDto

object SearchResultMapper {
    fun List<Tag>.toTagSearchDto() =
        TagSearchOutputDto(results = this.map { TagSearchResultOutputDto(tag = it.toDto()) })

    fun List<Person>.toPersonSearchDto() =
        PersonSearchOutputDto(results = this.map { PersonSearchResultOutputDto(person = it.toDto()) })
}
