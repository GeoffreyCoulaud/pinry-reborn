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
    fun setBoards(pinId: UUID, boardIds: List<UUID>, user: User): Pin {
        val pin = pinRepository.findPinById(id = pinId) ?: throw PinBoardSettingPinDoesNotExistError()
        if (pin.author != user) throw PinBoardSettingPermissionError()
        if (pin.softDeletedAt != null) throw PinBoardSettingSoftDeletedPinError()

        val boards = resolveBoards(boardIds = boardIds, user = user)
        // The fence re-reads the pin, so a recycling or a setTags landed since the read is kept, not restored.
        return pinRepository.saveFenced(transactionRunner, pinId, held = ::activeOrRefused) {
            it.copy(boards = boards, updatedAt = clock.now())
        } ?: throw PinBoardSettingPinDoesNotExistError()
    }

    private fun activeOrRefused(pin: Pin): Boolean {
        if (pin.softDeletedAt != null) throw PinBoardSettingSoftDeletedPinError()
        return true
    }

    /** The resolution half, outside any transaction, so [PinUpdater] runs it before opening its own. */
    fun resolveBoards(boardIds: List<UUID>, user: User): List<Board> = boardIds.map { resolveBoard(it, user) }

    // A board named in a body earns what a board named in a path earns (ADR 0038, decision 2).
    private fun resolveBoard(boardId: UUID, user: User): Board {
        val board = boardRepository.findActiveBoardById(boardId) ?: throw BoardRetrievalBoardDoesNotExistError()
        if (board.author != user) throw BoardRetrievalPermissionError()
        return board
    }
}
