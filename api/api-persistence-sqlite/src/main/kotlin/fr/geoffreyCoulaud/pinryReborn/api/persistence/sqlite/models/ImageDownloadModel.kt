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

@Entity
@Table(name = "image_download")
// One download row per pin, which `upsertPending` replaces rather than duplicates. It was the
// primary key until the row gained a surrogate id, and the uniqueness has to survive that.
@Index(
    name = "ux_image_download_pin",
    definition = "create unique index ux_image_download_pin on image_download (pin_id)",
)
@Suppress("LongParameterList") // Ebean entity: every parameter is a persisted column.
class ImageDownloadModel(
    id: UUID,
    var pinId: UUID,
    var sourceUrl: String,
    var status: String,
    var reasonCode: String?,
    var lastError: String?,
    var taskId: UUID,
    var requestedAt: Instant,
    var updatedAt: Instant,
) : BaseModel(id) {
    /**
     * The pin [pinId] names, so a query about it is a join rather than raw SQL. Ebean refuses `@Id`
     * on an association, which is why this is a second property on the column.
     */
    @ManyToOne
    @DbForeignKey(noIndex = true)
    @JoinColumn(name = "pin_id", insertable = false, updatable = false)
    lateinit var pin: PinModel
}
