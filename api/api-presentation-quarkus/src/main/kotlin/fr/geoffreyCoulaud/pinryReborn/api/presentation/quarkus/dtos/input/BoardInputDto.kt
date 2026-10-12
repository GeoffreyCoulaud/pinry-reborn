package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BoardInputDto(
    @field:NotBlank @field:Size(max = Board.MAX_NAME_LENGTH) val name: String,
    @field:Size(max = 2000) val description: String,
)
