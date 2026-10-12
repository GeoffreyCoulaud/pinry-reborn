package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Many-to-many join between pins and boards. Kept as a standalone join entity (like PinTagModel) to avoid
 * interdependency of models and repos.
 */
@Entity
@Table(name = "pin_boards")
// A pin is filed under a board once; `pin_id` leads, so it also serves every read by pin.
@Index(
    name = "ux_pin_boards_pin_board",
    definition = "create unique index ux_pin_boards_pin_board on pin_boards (pin_id, board_id)",
)
// Every read and delete by board: its pins, its count, recycling and emptying it.
@Index(name = "ix_pin_boards_board", columnNames = ["board_id"])
class PinBoardModel(
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE) var pin: PinModel,
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE) var board: BoardModel,
)
