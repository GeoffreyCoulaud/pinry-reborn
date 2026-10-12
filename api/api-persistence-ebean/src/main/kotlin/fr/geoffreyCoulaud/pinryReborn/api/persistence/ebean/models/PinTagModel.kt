package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** Many-to-many join between tags and pins. This is done to avoid interdependency of models and repos. */
@Entity
@Table(name = "pin_tags")
// A pin carries a tag once; `pin_id` leads, so it also serves every read by pin.
@Index(
    name = "ux_pin_tags_pin_tag",
    definition = "create unique index ux_pin_tags_pin_tag on pin_tags (pin_id, tag_id)",
)
// The search's tag subquery and the deletion of a tag's rows.
@Index(name = "ix_pin_tags_tag", columnNames = ["tag_id"])
class PinTagModel(
    // No index of Ebean's: the unique pair index leads with pin_id.
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE, noIndex = true) var pin: PinModel,
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE) var tag: TagModel,
)
