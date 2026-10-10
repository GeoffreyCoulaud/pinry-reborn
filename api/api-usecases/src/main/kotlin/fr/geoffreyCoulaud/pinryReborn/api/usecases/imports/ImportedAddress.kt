package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl

/** A line's mapper reading an address its check already passed, so a refusal here is a defect. */
internal object ImportedAddress {
    fun read(text: String): HttpUrl =
        checkNotNull(HttpUrl.parse(text)) { "the line's check let a refused address through: $text" }
}
