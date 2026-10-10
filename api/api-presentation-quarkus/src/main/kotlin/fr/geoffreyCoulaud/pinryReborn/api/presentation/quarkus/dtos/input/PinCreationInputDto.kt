package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.constraints.Size
import org.eclipse.microprofile.openapi.annotations.media.Schema

/**
 * Both source addresses are nullable: an image uploaded from disk names no media, and a pin found outside a page names
 * no page. Only null is no address.
 */
data class PinCreationInputDto(
    @field:HttpAddress @field:Schema(format = "uri") val sourceContextUrl: String?,
    @field:HttpAddress @field:Schema(format = "uri") val sourceMediaUrl: String?,
    @field:Size(max = 2000) val description: String,
)
