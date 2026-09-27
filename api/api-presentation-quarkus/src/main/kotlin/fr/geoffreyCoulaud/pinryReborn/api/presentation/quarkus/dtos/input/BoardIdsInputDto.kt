package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinIdsInputDto.Companion.MAX_IDENTIFIERS
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

/** The boards a batch route acts on, on [PinIdsInputDto]'s terms. */
data class BoardIdsInputDto(
    @field:NotEmpty
    @field:Size(max = MAX_IDENTIFIERS)
    val boardIds: List<UUID>,
)
