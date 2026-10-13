package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.NameBounds
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import jakarta.validation.constraints.Size
import java.net.URI
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

/** A person, found by its name and its addresses together or created; the server trims nothing. */
data class PersonInputDto(
    @field:NameText @field:Schema(pattern = "\\S", maxLength = NameBounds.MAX_LENGTH) val name: String,
    // `URI` only declares the items' `format: uri`, `@Schema` having no type-use target.
    @field:Size(max = Person.MAX_URLS)
    @field:Schema(type = SchemaType.ARRAY, implementation = URI::class)
    val urls: List<@HttpAddress String>,
)
