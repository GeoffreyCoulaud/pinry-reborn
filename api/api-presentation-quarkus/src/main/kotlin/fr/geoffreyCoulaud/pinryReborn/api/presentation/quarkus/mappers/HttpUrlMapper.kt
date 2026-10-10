package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl

object HttpUrlMapper {
    /** Called on a property `@HttpAddress` already validated, so a refusal here is a defect. */
    fun String.toHttpUrl(): HttpUrl = checkNotNull(HttpUrl.parse(this)) { "@HttpAddress let a refused address through" }
}
