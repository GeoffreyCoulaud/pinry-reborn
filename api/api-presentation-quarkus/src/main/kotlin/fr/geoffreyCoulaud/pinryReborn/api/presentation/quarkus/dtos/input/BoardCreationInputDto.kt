package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.NameBounds
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinIdsInputDto.Companion.MAX_IDENTIFIERS
import jakarta.validation.constraints.Size
import java.util.UUID
import org.eclipse.microprofile.openapi.annotations.media.Schema

/** [BoardInputDto] plus the pins filed under the new board; empty is allowed, this being a write of one board. */
data class BoardCreationInputDto(
    @field:NameText @field:Schema(pattern = "\\S", maxLength = NameBounds.MAX_LENGTH) val name: String,
    @field:Size(max = 2000) val description: String,
    @field:Size(max = MAX_IDENTIFIERS) val pinIds: List<UUID> = emptyList(),
)
