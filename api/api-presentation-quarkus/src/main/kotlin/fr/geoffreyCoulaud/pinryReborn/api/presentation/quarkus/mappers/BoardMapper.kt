package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardRefDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.RecycledBoardDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.RemoteCollectionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardSummary

object BoardMapper {
    fun Board.toRefDto() = BoardRefDto(id = id, name = name)

    fun Board.toDto(summary: BoardSummary) =
        BoardOutputDto(
            id = id,
            name = name,
            description = description,
            pinCount = summary.pinCount,
            coverUrl = summary.coverPinId?.let { PinMediaStateMapper.mediaUrl(it) },
            remoteCollections =
                summary.remoteCollections.map { RemoteCollectionOutputDto(name = it.name, url = it.url) },
        )

    fun Board.toRecycledDto() = RecycledBoardDto(id = id, name = name, description = description)
}
