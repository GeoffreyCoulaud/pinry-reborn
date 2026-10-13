package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.BoardName
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardRefDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.RecycledBoardDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.RemoteCollectionOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardSummary

object BoardMapper {
    fun Board.toRefDto() = BoardRefDto(id = id, name = name.text)

    fun Board.toDto(summary: BoardSummary) =
        BoardOutputDto(
            id = id,
            name = name.text,
            description = description,
            pinCount = summary.pinCount,
            coverUrl = summary.coverPinId?.let { PinMediaStateMapper.mediaUrl(it) },
            remoteCollections =
                summary.remoteCollections.map { RemoteCollectionOutputDto(name = it.name, url = it.url.toString()) },
        )

    fun Board.toRecycledDto() = RecycledBoardDto(id = id, name = name.text, description = description)

    /** Called on a DTO already validated, so a refused name here is a defect. */
    fun String.toBoardName(): BoardName =
        checkNotNull(BoardName.parse(this)) { "the constraints let a refused board name through" }
}
