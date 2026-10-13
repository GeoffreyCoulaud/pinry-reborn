package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.CollectionName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.BoardModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.BoardModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.HttpUrlModelMapper.toHttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.RemoteCollectionModel

object RemoteCollectionModelMapper {
    fun RemoteCollection.toModel() =
        RemoteCollectionModel(
            id = id,
            author = author.toModel(),
            url = url.toString(),
            name = name.text,
            board = board.toModel(),
            createdAt = createdAt,
        )

    fun RemoteCollectionModel.toDomain() =
        RemoteCollection(
            id = id,
            author = author.toDomain(),
            url = url.toHttpUrl(),
            name = checkNotNull(CollectionName.parse(name)) { "The stored collection name is refused: $name" },
            board = board.toDomain(),
            createdAt = createdAt,
        )
}
