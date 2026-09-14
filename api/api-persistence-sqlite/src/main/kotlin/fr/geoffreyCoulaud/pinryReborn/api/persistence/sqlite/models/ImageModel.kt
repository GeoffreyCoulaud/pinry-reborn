package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
import io.ebean.annotation.DbDefault
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "images")
// Not unique: two accounts, or two pins of one account, may legitimately hold the same bytes. The
// import probes this column once per pin, which without an index is pins times images.
@Index(name = "ix_images_content_hash", columnNames = ["content_hash"])
@Suppress("LongParameterList") // Ebean entity: every parameter is a persisted column.
class ImageModel(
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
) : BaseModel(id)
