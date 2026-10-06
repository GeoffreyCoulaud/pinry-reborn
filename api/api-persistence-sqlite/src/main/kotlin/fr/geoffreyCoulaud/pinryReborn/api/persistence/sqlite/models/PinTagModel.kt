package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne

/** Many-to-many join between tags and pins. This is done to avoid interdependency of models and repos. */
@Entity
// A pin carries a tag once; `pin_id` leads, so it also serves every read by pin.
@Index(
    name = "ux_pin_tag_model_pin_tag",
    definition = "create unique index ux_pin_tag_model_pin_tag on pin_tag_model (pin_id, tag_id)",
)
// The search's tag subquery and the deletion of a tag's rows.
@Index(name = "ix_pin_tag_model_tag", columnNames = ["tag_id"])
class PinTagModel(
    @ManyToOne var pin: PinModel,
    @ManyToOne var tag: TagModel,
)
