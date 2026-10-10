package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Instant
import java.util.UUID

/** A collection of a remote site, identified per author by its address and stored only linked to a board. */
data class RemoteCollection(
    override val id: UUID,
    val author: User,
    val url: HttpUrl,
    val name: String,
    val board: Board,
    val createdAt: Instant,
) : Identifiable
