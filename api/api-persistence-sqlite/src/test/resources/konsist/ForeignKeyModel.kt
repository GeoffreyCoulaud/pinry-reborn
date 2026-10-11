package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

// Never compiled: ModelsPackageArchTest asserts its key rule flags `tag` and `board`, and not `pin`.
@Entity
@Table(name = "pin_tags")
class PinTagModel(
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE) var pin: PinModel,
    @ManyToOne var tag: TagModel,
    @ManyToOne @DbForeignKey(noConstraint = true) var board: BoardModel,
)
