package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PaginationOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.BoardMapper.toRefDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PersonMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMediaStateMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.TagMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaState
import java.util.UUID

object PinMapper {
    /**
     * [mediaStates] and [pendingDuplicates] are what `statesFor` and `pendingAmong` returned for the pins being mapped:
     * parameters rather than defaults, so the compiler names every response that forgot to resolve them.
     */
    fun Pin.toDto(mediaStates: Map<UUID, PinMediaState>, pendingDuplicates: Set<UUID>) =
        PinOutputDto(
            id = id,
            authorId = author.id,
            sourceContextUrl = sourceContextUrl,
            sourceMediaUrl = sourceMediaUrl,
            description = description,
            tags = tags.map { it.toDto() },
            boards = boards.map { it.toRefDto() },
            publisher = publisher?.toDto(),
            creators = creators.map { it.toDto() },
            publishedAt = publishedAt,
            createdAt = createdAt,
            softDeletedAt = softDeletedAt,
            media = mediaStates[id]?.toDto(id),
            hasPendingDuplicates = id in pendingDuplicates,
        )

    fun Page<Pin>.toDto(mediaStates: Map<UUID, PinMediaState>, pendingDuplicates: Set<UUID>) =
        PinListOutputDto(
            pins = this.items.map { it.toDto(mediaStates, pendingDuplicates) },
            pagination =
                PaginationOutputDto(
                    previousCursor = this.previousCursor?.toDto(),
                    nextCursor = this.nextCursor?.toDto(),
                ),
        )
}
