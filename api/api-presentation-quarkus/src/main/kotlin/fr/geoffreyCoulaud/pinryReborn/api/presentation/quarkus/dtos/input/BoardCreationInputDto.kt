package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/** [BoardInputDto] plus the pins filed under the new board; empty is allowed, this being a write of one board. */
data class BoardCreationInputDto(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
    @field:Size(max = 2000)
    val description: String,
    val pinIds: List<UUID> = emptyList(),
)
