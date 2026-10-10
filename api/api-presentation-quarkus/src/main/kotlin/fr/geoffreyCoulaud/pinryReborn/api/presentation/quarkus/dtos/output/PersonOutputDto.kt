package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import java.net.URI
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

/** [urls] sorted by text, so a client may key a person by its name and its addresses as answered. */
data class PersonOutputDto(
    val name: String,
    // `URI` only declares the items' `format: uri`, as on `PersonInputDto`.
    @field:Schema(type = SchemaType.ARRAY, implementation = URI::class, uniqueItems = true) val urls: List<String>,
)

data class PersonSearchResultOutputDto(val person: PersonOutputDto)

data class PersonSearchOutputDto(val results: List<PersonSearchResultOutputDto>)
