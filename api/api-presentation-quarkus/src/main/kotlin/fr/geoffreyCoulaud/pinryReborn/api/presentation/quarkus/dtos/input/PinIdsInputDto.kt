package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

/** The pins a batch route acts on. Empty is a caller defect, so it answers 400 (ADR 0039, decision 3). */
data class PinIdsInputDto(
    @field:NotEmpty
    val pinIds: List<UUID>,
)
