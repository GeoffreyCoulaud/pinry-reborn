package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.BoardModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.BoardModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.RemoteCollectionModel

object RemoteCollectionModelMapper {
    fun RemoteCollection.toModel() =
        RemoteCollectionModel(
            id = id,
            author = author.toModel(),
            url = url,
            name = name,
            board = board.toModel(),
            createdAt = createdAt,
        )

    fun RemoteCollectionModel.toDomain() =
        RemoteCollection(
            id = id,
            author = author.toDomain(),
            url = url,
            name = name,
            board = board.toDomain(),
            createdAt = createdAt,
        )
}
