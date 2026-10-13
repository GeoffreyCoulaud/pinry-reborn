package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.TagName
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.TagOutputDto

object TagMapper {
    fun Tag.toDto() = TagOutputDto(name = name.text)

    /** Called on a DTO already validated, so a refused name here is a defect. */
    fun String.toTagName(): TagName =
        checkNotNull(TagName.parse(this)) { "the constraints let a refused tag name through" }
}
