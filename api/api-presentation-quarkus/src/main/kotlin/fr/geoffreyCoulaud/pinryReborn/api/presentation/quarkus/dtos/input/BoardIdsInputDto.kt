package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

/** The boards a batch route acts on, on [PinIdsInputDto]'s terms. */
data class BoardIdsInputDto(
    @field:NotEmpty
    val boardIds: List<UUID>,
)
