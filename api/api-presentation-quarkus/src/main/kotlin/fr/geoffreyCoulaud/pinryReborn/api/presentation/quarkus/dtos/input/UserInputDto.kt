package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UserInputDto(
    @field:NotBlank
    @field:Size(min = 3, max = User.MAX_NAME_LENGTH)
    @field:Pattern(regexp = User.NAME_PATTERN)
    val name: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val password: String,
)
