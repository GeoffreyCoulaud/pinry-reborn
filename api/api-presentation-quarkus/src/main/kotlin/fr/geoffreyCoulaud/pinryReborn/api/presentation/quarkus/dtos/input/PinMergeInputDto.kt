package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

/** The pin kept and the pins it absorbs, each named once (ADR 0039, decision 3). */
data class PinMergeInputDto(
    val keptPinId: UUID,
    @field:NotEmpty
    @field:Size(max = PinIdsInputDto.MAX_IDENTIFIERS)
    val absorbedPinIds: List<UUID>,
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "names a pin twice, or the kept pin among the absorbed")
    val isEachPinNamedOnce: Boolean
        get() = (absorbedPinIds + keptPinId).toSet().size == absorbedPinIds.size + 1
}
