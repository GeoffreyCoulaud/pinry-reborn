package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** Many-to-many join between pins and the persons who made them, a standalone entity as PinTagModel is. */
@Entity
@Table(name = "pin_creators")
// A pin credits a creator once; `pin_id` leads, so it also serves every read by pin.
@Index(
    name = "ux_pin_creators_pin_person",
    definition = "create unique index ux_pin_creators_pin_person on pin_creators (pin_id, person_id)",
)
@Index(name = "ix_pin_creators_person", columnNames = ["person_id"])
class PinCreatorModel(
    // No index of Ebean's: the unique pair index leads with pin_id.
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE, noIndex = true) var pin: PinModel,
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE) var person: PersonModel,
)
