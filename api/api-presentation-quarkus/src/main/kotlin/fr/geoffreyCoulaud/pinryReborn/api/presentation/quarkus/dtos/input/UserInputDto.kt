package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Username
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.eclipse.microprofile.openapi.annotations.media.Schema

data class UserInputDto(
    @field:AccountName
    @field:Schema(pattern = Username.PATTERN, minLength = Username.MIN_LENGTH, maxLength = Username.MAX_LENGTH)
    val name: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val password: String,
)
