package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

data class PersonOutputDto(val name: String, val urls: List<String>)

data class PersonSearchResultOutputDto(val person: PersonOutputDto)

data class PersonSearchOutputDto(val results: List<PersonSearchResultOutputDto>)
