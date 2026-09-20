package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalBoardDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinBoardSettingSoftDeletedPinError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PinBoardSetter(
    private val pinRepository: PinRepositoryInterface,
    private val boardRepository: BoardRepositoryInterface,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    /** All or nothing: the board and every pin are resolved before the first write (ADR 0039, decision 2). */
    fun addPinsToBoard(boardId: UUID, pinIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        val board = resolveBoard(boardId, user)
        val pins = pinIds.map { resolvePin(pinId = it, user = user) }
        val at = clock.now()
        pins.forEach { pinRepository.savePin(it.copy(boards = it.boardsWithout(board) + board, updatedAt = at)) }
    }

    /** All or nothing, as [addPinsToBoard] is. */
    fun removePinsFromBoard(boardId: UUID, pinIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        val board = resolveBoard(boardId, user)
        val pins = pinIds.map { resolvePin(pinId = it, user = user) }
        val at = clock.now()
        pins.forEach { pinRepository.savePin(it.copy(boards = it.boardsWithout(board), updatedAt = at)) }
    }

    // savePin diffs only the active memberships, so rewriting this list leaves a recycled board's join row alone.
    private fun Pin.boardsWithout(board: Board): List<Board> = boards.filterNot { it.id == board.id }

    @Suppress("ThrowsCount") // The three refusals a pin earns, wherever it was named.
    private fun resolvePin(pinId: UUID, user: User): Pin {
        val pin = pinRepository.findPinById(id = pinId) ?: throw PinBoardSettingPinDoesNotExistError()
        if (pin.author != user) throw PinBoardSettingPermissionError()
        if (pin.softDeletedAt != null) throw PinBoardSettingSoftDeletedPinError()
        return pin
    }

    /** The resolution half, split from the write, so [PinUpdater] runs it inside its own transaction. */
    fun resolveBoards(boardIds: List<UUID>, user: User): List<Board> = boardIds.map { resolveBoard(it, user) }

    // A board named in a body earns what a board named in a path earns (ADR 0038, decision 2).
    private fun resolveBoard(boardId: UUID, user: User): Board {
        val board = boardRepository.findActiveBoardById(boardId) ?: throw BoardRetrievalBoardDoesNotExistError()
        if (board.author != user) throw BoardRetrievalPermissionError()
        return board
    }
}
