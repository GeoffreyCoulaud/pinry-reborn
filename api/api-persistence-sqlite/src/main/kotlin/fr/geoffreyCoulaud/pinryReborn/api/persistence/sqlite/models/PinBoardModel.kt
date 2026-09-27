package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne

/**
 * Many-to-many join between pins and boards.
 * Kept as a standalone join entity (like PinTagModel) to avoid interdependency of models and repos.
 */
@Entity
// A pin is filed under a board once; `pin_id` leads, so it also serves every read by pin.
@Index(
    name = "ux_pin_board_model_pin_board",
    definition = "create unique index ux_pin_board_model_pin_board on pin_board_model (pin_id, board_id)",
)
// Every read and delete by board: its pins, its count, recycling and emptying it.
@Index(name = "ix_pin_board_model_board", columnNames = ["board_id"])
class PinBoardModel(
    @ManyToOne var pin: PinModel,
    @ManyToOne var board: BoardModel,
)
