package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotNull

/** [rejected] is nullable so a missing one is refused: Jackson reads an absent `Boolean` as false. */
data class PinDuplicateUpdateInputDto(
    @field:NotNull
    val rejected: Boolean?,
)
