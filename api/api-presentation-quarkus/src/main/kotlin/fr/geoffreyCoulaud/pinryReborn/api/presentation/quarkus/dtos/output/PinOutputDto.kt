package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import java.time.Instant
import java.util.*
import org.eclipse.microprofile.openapi.annotations.media.Schema

data class PinOutputDto(
    val id: UUID,
    val authorId: UUID,
    @field:Schema(format = "uri") val sourceContextUrl: String?,
    @field:Schema(format = "uri") val sourceMediaUrl: String?,
    val description: String,
    val tags: List<TagOutputDto>,
    val boards: List<BoardRefDto>,
    val publisher: PersonOutputDto?,
    val creators: List<PersonOutputDto>,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val softDeletedAt: Instant? = null,
    /** The pin's image state, or null when the pin has neither an image nor a download. */
    val media: PinMediaStateDto? = null,
    /** The pin has a likely duplicate the user has not rejected, both pins being active. */
    val hasPendingDuplicates: Boolean,
)
