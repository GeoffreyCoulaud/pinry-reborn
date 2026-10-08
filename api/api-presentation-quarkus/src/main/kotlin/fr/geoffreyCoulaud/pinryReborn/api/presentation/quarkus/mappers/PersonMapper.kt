package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PersonInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonReference

object PersonMapper {
    fun Person.toDto() = PersonOutputDto(name = name, urls = urls)

    fun PersonInputDto.toReference() = PersonReference(name = name, urls = urls)
}
