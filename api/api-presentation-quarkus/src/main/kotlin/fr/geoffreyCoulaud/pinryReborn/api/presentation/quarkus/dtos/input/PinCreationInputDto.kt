package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.Size

/**
 * Both source addresses are nullable: an image uploaded from disk names no media, and a pin found
 * outside a page names no page. The controller reads a blank one as none.
 */
data class PinCreationInputDto(
    val sourceContextUrl: String?,
    val sourceMediaUrl: String?,
    @field:Size(max = 2000)
    val description: String,
)
