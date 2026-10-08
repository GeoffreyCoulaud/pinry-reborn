package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID.randomUUID

/** The import's collections (specification 2026-10-08, decision F), linked to a board by name whatever its state. */
@ApplicationScoped
class RemoteCollectionLinker(
    private val remoteCollectionRepository: RemoteCollectionRepositoryInterface,
    private val boardRepository: BoardRepositoryInterface,
    private val transactionRunner: TransactionRunner,
) {
    /**
     * The reads and the writes in one transaction, so a concurrent import converges on one row. An address the user
     * already holds keeps its link; a board name nobody holds is created, empty, at [createdAt]. True when it was.
     */
    fun link(user: User, url: String, name: String, boardName: String, createdAt: Instant): Boolean =
        transactionRunner.inTransaction {
            if (remoteCollectionRepository.findUserRemoteCollectionByUrl(user, url) != null) {
                false
            } else {
                val existing = boardRepository.findBoardForUserByName(user, boardName)
                val board = existing ?: createBoard(user, boardName, createdAt)
                remoteCollectionRepository.saveRemoteCollection(
                    RemoteCollection(randomUUID(), user, url, name, board, createdAt)
                )
                existing == null
            }
        }

    /** The board the user's collection at [url] links to, or null for an address the user does not hold. */
    fun boardOf(user: User, url: String): Board? =
        remoteCollectionRepository.findUserRemoteCollectionByUrl(user, url)?.board

    private fun createBoard(user: User, name: String, createdAt: Instant): Board =
        boardRepository.saveBoard(
            Board(
                id = randomUUID(),
                author = user,
                name = name,
                description = "",
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        )
}
