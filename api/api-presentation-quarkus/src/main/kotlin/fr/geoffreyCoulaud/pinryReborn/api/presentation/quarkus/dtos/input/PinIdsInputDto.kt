package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

/**
 * The pins a batch route acts on. Empty is a caller defect, not an identifier that failed to
 * resolve, so it answers 400 (`docs/adr/0039-a-batch-route-is-all-or-nothing.md`, decision 3).
 */
data class PinIdsInputDto(
    @field:NotEmpty
    val pinIds: List<UUID>,
) {
    companion object {
        /** The contract's wording for every batch route, this one's and [BoardIdsInputDto]'s. */
        const val ALL_OR_NOTHING =
            "All or nothing: every identifier is resolved before the first write, and one that is " +
                "unknown (404) or another user's (403) refuses the whole call with nothing written."
    }
}
