package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import java.util.UUID

/**
 * Reads the board, checks it, and saves [update] of it in one transaction; null when it is absent or refused.
 * `saveBoard` writes every column from the copy, `softDeletedAt` included, so the copy is read here.
 */
internal fun BoardRepositoryInterface.saveFenced(
    transactionRunner: TransactionRunner,
    boardId: UUID,
    held: (Board) -> Boolean,
    update: (Board) -> Board,
): Board? = transactionRunner.fenced({ findBoardById(boardId) }, held, update) { saveBoard(it) }
