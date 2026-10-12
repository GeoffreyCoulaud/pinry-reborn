package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl

object HttpUrlModelMapper {
    /** A stored address the factory refuses is a row to reset, never one to read as no address. */
    fun String.toHttpUrl(): HttpUrl = checkNotNull(HttpUrl.parse(this)) { "The stored address is refused: $this" }
}
