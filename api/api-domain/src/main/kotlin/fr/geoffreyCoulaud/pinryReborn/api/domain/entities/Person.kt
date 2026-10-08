package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Instant
import java.util.UUID

/** Identified per author by its name, folded on ASCII case, and its set of addresses together. */
data class Person(
    override val id: UUID,
    val author: User,
    val name: String,
    val urls: List<String>,
    val createdAt: Instant,
) : Identifiable
