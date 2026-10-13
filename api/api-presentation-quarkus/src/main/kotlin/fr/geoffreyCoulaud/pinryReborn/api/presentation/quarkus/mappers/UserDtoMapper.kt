package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Username
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserOutputDto

object UserDtoMapper {
    fun User.toDto() =
        UserOutputDto(
            id = id,
            name = name.text,
        )

    /** Called on a DTO already validated, so a refused name here is a defect. */
    fun String.toUsername(): Username =
        checkNotNull(Username.parse(this)) { "the constraints let a refused account name through" }
}
