package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotNull
import org.eclipse.microprofile.openapi.annotations.media.Schema

/** [rejected] is nullable so a missing one is refused: Jackson reads an absent `Boolean` as false. */
data class PinDuplicateUpdateInputDto(
    // Published as a plain boolean, null being refused like a missing key.
    @field:NotNull
    @field:Schema(nullable = false)
    val rejected: Boolean?,
)
