package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PersonInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.HttpUrlMapper.toHttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonReference

object PersonMapper {
    fun Person.toDto() = PersonOutputDto(name = name.text, urls = urls.map { it.toString() }.sorted())

    /** Called on a DTO already validated, so a refused name here is a defect. */
    fun PersonInputDto.toReference() =
        PersonReference(
            name = checkNotNull(PersonName.parse(name)) { "the constraints let a refused name through" },
            urls = urls.map { it.toHttpUrl() }.toSet(),
        )
}
