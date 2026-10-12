package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

/** The pins a batch route acts on. Empty is a caller defect, so it answers 400 (ADR 0039, decision 3). */
data class PinIdsInputDto(@field:NotEmpty @field:Size(max = MAX_IDENTIFIERS) val pinIds: List<UUID>) {
    companion object {
        /** Under the 65 535 bound parameters of a PostgreSQL statement, which a bulk read spends one per identifier. */
        const val MAX_IDENTIFIERS = 10_000
    }
}
