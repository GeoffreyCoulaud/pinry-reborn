package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Two pins whose media look alike, [firstPinId] the lower id; [rejectedAt] is set once the user says they are not. */
@Entity
@Table(name = "pin_duplicates")
// A pair is held once; `first_pin_id` leads, so it also serves every read by that side.
@Index(
    name = "ux_pin_duplicates_pins",
    definition = "create unique index ux_pin_duplicates_pins on pin_duplicates (first_pin_id, second_pin_id)",
)
// Every read by the other side, and the deletion of its pin.
@Index(name = "ix_pin_duplicates_second_pin", columnNames = ["second_pin_id"])
class PinDuplicateModel(
    id: UUID,
    var firstPinId: UUID,
    var secondPinId: UUID,
    var rejectedAt: Instant?,
) : BaseModel(id) {
    // Not optional, so a read joins them inner.
    @ManyToOne(optional = false)
    @DbForeignKey(onDelete = ConstraintMode.CASCADE)
    @JoinColumn(name = "first_pin_id", insertable = false, updatable = false)
    lateinit var firstPin: PinModel

    @ManyToOne(optional = false)
    @DbForeignKey(onDelete = ConstraintMode.CASCADE)
    @JoinColumn(name = "second_pin_id", insertable = false, updatable = false)
    lateinit var secondPin: PinModel
}
