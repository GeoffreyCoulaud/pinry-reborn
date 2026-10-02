package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.NotBlank

data class PinMediaDownloadInputDto(
    @field:NotBlank
    val sourceUrl: String,
)
