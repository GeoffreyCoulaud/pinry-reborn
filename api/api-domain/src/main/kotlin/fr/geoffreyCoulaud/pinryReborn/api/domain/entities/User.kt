package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Instant
import java.util.UUID

data class User(
    override val id: UUID,
    val name: String,
    val createdAt: Instant,
    val softDeletedAt: Instant? = null,
) : Identifiable {
    companion object {
        /** The account alphabet, where the store's case fold and a lower-casing agree (ADR 0059, decision 11). */
        const val NAME_PATTERN = "^[A-Za-z0-9._-]+$"
    }
}
