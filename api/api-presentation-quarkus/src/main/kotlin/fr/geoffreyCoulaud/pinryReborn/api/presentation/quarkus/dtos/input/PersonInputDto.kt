package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** A person, found by its name and its addresses together or created; the server trims nothing. */
data class PersonInputDto(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 20) val urls: List<@NotBlank @Size(max = 2000) String>,
)
