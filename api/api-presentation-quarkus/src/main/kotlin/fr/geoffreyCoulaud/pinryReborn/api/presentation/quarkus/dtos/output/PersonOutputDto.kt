package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import org.eclipse.microprofile.openapi.annotations.media.Schema

/** [urls] sorted by text, so a client may key a person by its name and its addresses as answered. */
data class PersonOutputDto(val name: String, @field:Schema(uniqueItems = true) val urls: List<String>)

data class PersonSearchResultOutputDto(val person: PersonOutputDto)

data class PersonSearchOutputDto(val results: List<PersonSearchResultOutputDto>)
