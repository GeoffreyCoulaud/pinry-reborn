package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.BoardModel

object BoardModelMapper {
    fun Board.toModel() =
        BoardModel(
            id = id,
            author = author.toModel(),
            name = name,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            softDeletedAt = softDeletedAt,
        )

    fun BoardModel.toDomain() =
        Board(
            id = id,
            author = author.toDomain(),
            name = name,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            softDeletedAt = softDeletedAt,
        )
}
