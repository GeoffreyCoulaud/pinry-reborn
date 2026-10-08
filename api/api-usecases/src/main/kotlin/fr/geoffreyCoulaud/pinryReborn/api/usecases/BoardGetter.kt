package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalBoardDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalPermissionError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** What a board shows beside its name: its active pin count, its cover pin, and the collections linked to it. */
data class BoardSummary(val pinCount: Int, val coverPinId: UUID?, val remoteCollections: List<RemoteCollection>)

@ApplicationScoped
class BoardGetter(
    private val boardRepository: BoardRepositoryInterface,
    private val remoteCollectionRepository: RemoteCollectionRepositoryInterface,
) {
    fun getActiveBoardForUser(boardId: UUID, reader: User): Board {
        val board = boardRepository.findActiveBoardById(boardId) ?: throw BoardRetrievalBoardDoesNotExistError()
        if (board.author != reader) throw BoardRetrievalPermissionError()
        return board
    }

    fun listActiveBoardsForUser(reader: User): List<Board> = boardRepository.findActiveBoardsForUser(reader)

    fun summarizeActiveBoardForUser(boardId: UUID, reader: User): BoardSummary {
        getActiveBoardForUser(boardId = boardId, reader = reader)
        return BoardSummary(
            pinCount = boardRepository.countActivePinsInBoard(boardId),
            coverPinId = boardRepository.findCoverPinId(boardId),
            remoteCollections = remoteCollectionRepository.findRemoteCollectionsForBoard(boardId),
        )
    }
}
