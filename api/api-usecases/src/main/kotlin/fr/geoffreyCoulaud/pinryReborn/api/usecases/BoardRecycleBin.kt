package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardDeletionBoardAlreadySoftDeletedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardDeletionBoardDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardDeletionBoardNotSoftDeletedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardDeletionPermissionError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class BoardRecycleBin(
    private val boardRepository: BoardRepositoryInterface,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    // Find the board regardless of state, then validate ownership BEFORE checking state, so a
    // missing board is 404, a non-owner is 403, and a wrong-state board is 409 (mirrors
    // PinRecycleBin.findPinAndValidateOwnership).
    private fun findBoardAndValidateOwnership(boardId: UUID, user: User): Board {
        val board = boardRepository.findBoardById(boardId) ?: throw BoardDeletionBoardDoesNotExistError()
        if (board.author != user) throw BoardDeletionPermissionError()
        return board
    }

    fun softDelete(boardId: UUID, user: User): Board {
        val board = findBoardAndValidateOwnership(boardId, user)
        if (board.softDeletedAt != null) throw BoardDeletionBoardAlreadySoftDeletedError()
        return boardRepository.softDeleteBoard(board = board, at = clock.now())
    }

    private fun recycledOrRefused(boardId: UUID, user: User): Board =
        findBoardAndValidateOwnership(boardId, user).also {
            if (it.softDeletedAt == null) throw BoardDeletionBoardNotSoftDeletedError()
        }

    fun restore(boardId: UUID, user: User): Board =
        boardRepository.restoreBoard(board = recycledOrRefused(boardId, user), at = clock.now())

    /** All or nothing: every board is resolved before the first write (ADR 0039, decision 2). */
    fun restoreAll(boardIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        val boards = boardIds.map { recycledOrRefused(it, user) }
        val at = clock.now()
        boards.forEach { boardRepository.restoreBoard(board = it, at = at) }
    }

    fun permanentlyDelete(boardId: UUID, user: User) =
        boardRepository.permanentlyDeleteBoard(recycledOrRefused(boardId, user))

    fun emptyRecycleBin(user: User) =
        boardRepository.permanentlyDeleteAllRecycledBoardsForUser(user)

    fun listRecycledBoardsForUser(user: User): List<Board> =
        boardRepository.findRecycledBoardsForUser(user)
}
