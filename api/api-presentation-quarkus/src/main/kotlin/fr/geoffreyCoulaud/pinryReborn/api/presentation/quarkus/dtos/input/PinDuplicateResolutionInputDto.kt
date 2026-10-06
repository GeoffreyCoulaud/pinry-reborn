package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.Size
import java.util.UUID

/** One decision per pin of the open pin's group, the open pin's included (ADR 0052, decision 3). */
data class PinDuplicateResolutionInputDto(
    @field:Size(max = PinIdsInputDto.MAX_IDENTIFIERS) val decisions: Map<UUID, DuplicateDecisionInputEnum>
)
