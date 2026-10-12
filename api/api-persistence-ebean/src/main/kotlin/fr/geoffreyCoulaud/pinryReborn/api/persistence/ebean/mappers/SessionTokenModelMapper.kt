package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.SessionToken
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.SessionTokenModel

object SessionTokenModelMapper {
    fun SessionTokenModel.toDomain() =
        SessionToken(
            id = id,
            user = user.toDomain(),
            expiresAt = expiresAt,
            persistent = persistent,
            createdAt = createdAt,
        )
}
