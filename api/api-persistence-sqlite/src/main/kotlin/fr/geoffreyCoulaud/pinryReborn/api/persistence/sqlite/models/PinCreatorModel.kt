package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne

/** Many-to-many join between pins and the persons who made them, a standalone entity as PinTagModel is. */
@Entity
// A pin credits a creator once; `pin_id` leads, so it also serves every read by pin.
@Index(
    name = "ux_pin_creator_model_pin_person",
    definition = "create unique index ux_pin_creator_model_pin_person on pin_creator_model (pin_id, person_id)",
)
@Index(name = "ix_pin_creator_model_person", columnNames = ["person_id"])
class PinCreatorModel(
    @ManyToOne var pin: PinModel,
    @ManyToOne var person: PersonModel,
)
