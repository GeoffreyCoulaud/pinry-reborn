package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Instant
import java.util.UUID

data class User(
    override val id: UUID,
    val name: Username,
    val createdAt: Instant,
    val softDeletedAt: Instant? = null,
) : Identifiable
