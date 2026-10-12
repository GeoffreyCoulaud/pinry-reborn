package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.boards.BoardNameAlreadyTakenException
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.BoardModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.BoardModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.BoardModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QMediaModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QPinBoardModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.queries.BoardQueries
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.queries.orderedByName
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.queries.withActivePin
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.PersistenceException
import java.time.Instant
import java.util.UUID

@ApplicationScoped
// BoardRepositoryInterface's surface (13 methods) trips detekt's default per-class threshold.
// Suppressed rather than split, since splitting would
// fragment one cohesive adapter across artificial classes for no readability gain (mirrors
// PinRepository's precedent for the same rule).
@Suppress("TooManyFunctions")
class BoardRepository(private val persistor: Persistor) : BoardRepositoryInterface {
    private val sqlRepository = ModelRepository<BoardModel>(persistor = persistor)

    override fun saveBoard(board: Board): Board =
        try {
            sqlRepository.saveAndReturn(board.toModel()).toDomain()
        } catch (error: PersistenceException) {
            // A taken name, on creation and renaming; restoreBoard changes no indexed column.
            ConstraintViolations.translateUniqueConstraint(error, "ux_boards_author_name") {
                BoardNameAlreadyTakenException(cause = it)
            }
        }

    override fun findBoardById(id: UUID): Board? = BoardQueries.any().id.equalTo(id).findOne()?.toDomain()

    // Reads every state through BoardQueries.any(): a recycled board holds its name (ADR 0008 asks
    // the state be named). A plain equality, the column's collation folding as its unique index does.
    override fun findBoardForUserByName(user: User, name: String): Board? =
        BoardQueries.any().author.id.equalTo(user.id).name.eq(name).findOne()?.toDomain()

    override fun findActiveBoardById(id: UUID): Board? = BoardQueries.active().id.equalTo(id).findOne()?.toDomain()

    override fun findActiveBoardsForUser(user: User): List<Board> =
        BoardQueries.active().author.id.equalTo(user.id).orderedByName().findList().map { it.toDomain() }

    override fun findRecycledBoardsForUser(user: User): List<Board> =
        BoardQueries.recycled().author.id.equalTo(user.id).orderedByName().findList().map { it.toDomain() }

    override fun softDeleteBoard(board: Board, at: Instant): Board {
        val model =
            checkNotNull(BoardQueries.any().id.equalTo(board.id).findOne()) {
                "board ${board.id} vanished between read and soft-delete transition"
            }
        model.softDeletedAt = at
        model.updatedAt = at
        persistor.save(model)
        return model.toDomain()
    }

    override fun restoreBoard(board: Board, at: Instant): Board {
        val model =
            checkNotNull(BoardQueries.any().id.equalTo(board.id).findOne()) {
                "board ${board.id} vanished between read and restore transition"
            }
        model.softDeletedAt = null
        model.updatedAt = at
        persistor.save(model)
        return model.toDomain()
    }

    override fun permanentlyDeleteBoard(board: Board) {
        BoardQueries.any().id.equalTo(board.id).delete()
    }

    override fun permanentlyDeleteAllRecycledBoardsForUser(user: User) {
        BoardQueries.recycled().author.id.equalTo(user.id).delete()
    }

    override fun countActivePinsInBoard(boardId: UUID): Int =
        QPinBoardModel().board.id.equalTo(boardId).withActivePin().findCount()

    override fun findCoverPinId(boardId: UUID): UUID? {
        val filed = QPinBoardModel().board.id.equalTo(boardId).select("pin.id")
        return QMediaModel()
            .withActivePin()
            .pinId
            .isIn(filed.query())
            .orderBy()
            .pin
            .createdAt
            .desc()
            .pinId
            .desc()
            .setMaxRows(1)
            .select("pinId")
            .findSingleAttribute<UUID>()
    }
}
