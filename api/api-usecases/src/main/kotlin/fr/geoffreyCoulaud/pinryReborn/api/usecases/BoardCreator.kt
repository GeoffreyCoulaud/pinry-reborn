package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.boards.BoardNameAlreadyTakenException
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BoardNameAlreadyExistsError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.UUID.randomUUID

@ApplicationScoped
class BoardCreator(
    private val boardRepository: BoardRepositoryInterface,
    private val pinRepository: PinRepositoryInterface,
    private val pinBoardSetter: PinBoardSetter,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    /** All or nothing, as a batch route is (ADR 0039): a refused pin leaves no empty board behind. */
    fun create(author: User, name: String, description: String, pinIds: List<UUID> = emptyList()): Board {
        val now = clock.now()
        return try {
            transactionRunner.inTransaction {
                val pins = pinBoardSetter.resolvePins(pinIds = pinIds.distinct(), user = author)
                val board = boardRepository.saveBoard(
                    Board(
                        id = randomUUID(),
                        author = author,
                        name = name,
                        description = description,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                pinRepository.addPinsToBoard(pinIds = pins.map { it.id }, board = board, at = now)
                board
            }
        } catch (error: BoardNameAlreadyTakenException) {
            // Read after the refusal, never before it: the index answers uniqueness, this only
            // decides whether the client is told the recycle bin is holding the name.
            throw BoardNameAlreadyExistsError(
                holder = boardRepository.findBoardForUserByName(user = author, name = name),
                cause = error,
            )
        }
    }
}
