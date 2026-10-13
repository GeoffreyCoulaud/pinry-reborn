package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.boards.BoardNameAlreadyTakenException
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.BoardName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardNameAlreadyExistsError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardRetrievalBoardDoesNotExistError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class BoardUpdater(
    private val boardRepository: BoardRepositoryInterface,
    private val boardGetter: BoardGetter,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    fun update(boardId: UUID, name: BoardName, description: String, user: User): Board {
        boardGetter.getActiveBoardForUser(boardId = boardId, reader = user)
        return try {
            // The fence re-reads the board, so a recycling landed since the read is kept, not restored.
            boardRepository.saveFenced(transactionRunner, boardId, held = { it.softDeletedAt == null }) {
                it.copy(name = name, description = description, updatedAt = clock.now())
            } ?: throw BoardRetrievalBoardDoesNotExistError()
        } catch (error: BoardNameAlreadyTakenException) {
            // Same read-after-refusal as BoardCreator: renaming is the second of the sites the index
            // refuses, and the client is told when the holder sits in the recycle bin.
            throw BoardNameAlreadyExistsError(
                holder = boardRepository.findBoardForUserByName(user = user, name = name),
                cause = error,
            )
        }
    }
}
