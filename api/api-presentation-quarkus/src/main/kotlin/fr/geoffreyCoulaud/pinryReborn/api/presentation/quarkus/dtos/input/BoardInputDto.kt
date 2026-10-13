package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.NameBounds
import jakarta.validation.constraints.Size
import org.eclipse.microprofile.openapi.annotations.media.Schema

data class BoardInputDto(
    @field:NameText @field:Schema(pattern = "\\S", maxLength = NameBounds.MAX_LENGTH) val name: String,
    @field:Size(max = 2000) val description: String,
)
