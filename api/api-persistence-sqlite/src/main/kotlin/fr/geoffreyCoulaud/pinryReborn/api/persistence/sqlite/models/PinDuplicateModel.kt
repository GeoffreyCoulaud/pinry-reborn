package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
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
@Table(name = "pin_duplicate")
// A pair is held once; `first_pin_id` leads, so it also serves every read by that side.
@Index(
    name = "ux_pin_duplicate_pins",
    definition = "create unique index ux_pin_duplicate_pins on pin_duplicate (first_pin_id, second_pin_id)",
)
// Every read by the other side; `noConstraint` drops the index a foreign key would bring.
@Index(name = "ix_pin_duplicate_second_pin", columnNames = ["second_pin_id"])
class PinDuplicateModel(
    id: UUID,
    var firstPinId: UUID,
    var secondPinId: UUID,
    var rejectedAt: Instant?,
) : BaseModel(id) {
    // No constraints: the orphan sweep deletes a gone pin's pairs, so a pin's delete never waits on them.
    @ManyToOne
    @DbForeignKey(noConstraint = true)
    @JoinColumn(name = "first_pin_id", insertable = false, updatable = false)
    lateinit var firstPin: PinModel

    @ManyToOne
    @DbForeignKey(noConstraint = true)
    @JoinColumn(name = "second_pin_id", insertable = false, updatable = false)
    lateinit var secondPin: PinModel
}
