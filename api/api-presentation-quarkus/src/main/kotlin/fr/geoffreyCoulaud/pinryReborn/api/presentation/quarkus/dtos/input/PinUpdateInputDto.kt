package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * The whole pin, replaced: an omitted field is refused and an empty list clears
 * (`docs/adr/0038-one-route-writes-a-pin.md`).
 */
data class PinUpdateInputDto(
    val sourceContextUrl: String?,
    val sourceMediaUrl: String?,
    @field:Size(max = 2000) val description: String,
    val tags: List<@NotBlank String>,
    val boardIds: List<UUID>,
    @field:Valid val publisher: PersonInputDto?,
    @field:Size(max = 100) val creators: List<@Valid PersonInputDto>,
    val publishedAt: Instant?,
)
