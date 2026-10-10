package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Instant
import java.util.UUID

data class Pin(
    override val id: UUID,
    val author: User,
    val sourceContextUrl: HttpUrl?,
    val sourceMediaUrl: HttpUrl?,
    val description: String,
    val tags: List<Tag>,
    val boards: List<Board>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val softDeletedAt: Instant? = null,
    val media: Media? = null,
    val publisher: Person? = null,
    val creators: List<Person> = emptyList(),
    val publishedAt: Instant? = null,
) : Identifiable
