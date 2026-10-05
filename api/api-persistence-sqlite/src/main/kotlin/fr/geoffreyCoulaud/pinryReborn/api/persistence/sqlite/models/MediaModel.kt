package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
import io.ebean.annotation.DbDefault
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "media")
// Not unique: two accounts, or two pins of one account, may legitimately hold the same bytes. The
// import probes this column once per pin, which without an index is pins times images.
@Index(name = "ix_media_content_hash", columnNames = ["content_hash"])
@Suppress("LongParameterList") // Ebean entity: every parameter is a persisted column.
class MediaModel(
    id: UUID,
    @Column(unique = true) var pinId: UUID,
    var mimeType: String,
    var width: Int,
    var height: Int,
    @DbDefault("false") var animated: Boolean,
    var byteSize: Long,
    var contentHash: String,
    var storageKey: String,
    var createdAt: Instant,
    @DbDefault("1") var frames: Int,
    var durationMillis: Long?,
    var videoBitRate: Long?,
    var audioChannels: Int?,
    var audioBitRate: Long?,
    var probeVersion: Int?,
) : BaseModel(id) {
    /** The fingerprint algorithm's version that hashed this media's frames, null until hashed. */
    var fingerprintVersion: Int? = null

    /**
     * The pin [pinId] names, so a query about it is a join rather than raw SQL. No index of its own:
     * `uq_media_pin_id` already covers the column.
     */
    @ManyToOne
    @DbForeignKey(noIndex = true)
    @JoinColumn(name = "pin_id", insertable = false, updatable = false)
    lateinit var pin: PinModel
}
