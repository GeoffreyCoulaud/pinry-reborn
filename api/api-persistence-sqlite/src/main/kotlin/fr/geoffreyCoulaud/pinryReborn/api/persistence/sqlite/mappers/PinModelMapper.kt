package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.HttpUrlModelMapper.toHttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PinModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination.ModelCursor

object PinModelMapper {
    fun Pin.toModel(): PinModel =
        PinModel(
            id = id,
            author = author.toModel(),
            sourceContextUrl = sourceContextUrl?.toString(),
            sourceMediaUrl = sourceMediaUrl?.toString(),
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            softDeletedAt = softDeletedAt,
            publisher = publisher?.toModel(),
            publishedAt = publishedAt,
        )

    fun PinModel.toDomain(
        tags: List<Tag>,
        boards: List<Board>,
        creators: List<Person>,
    ): Pin =
        Pin(
            id = id,
            author = author.toDomain(),
            sourceContextUrl = sourceContextUrl?.toHttpUrl(),
            sourceMediaUrl = sourceMediaUrl?.toHttpUrl(),
            description = description,
            tags = tags,
            boards = boards,
            createdAt = createdAt,
            updatedAt = updatedAt,
            softDeletedAt = softDeletedAt,
            publisher = publisher?.toDomain(),
            creators = creators,
            publishedAt = publishedAt,
        )

    fun ModelCursor<PinModel>.toDomain(): Cursor =
        Cursor(
            pivotId = this.pivot.id,
            direction = this.direction,
        )
}
