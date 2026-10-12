package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.BaseModel
import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
@Entity
@Table(name = "user_data_imports")
// One active import per user. Partial, so `definition` carries the `where` and nothing else is set:
// `unique = true` alongside it makes Ebean emit an ALTER TABLE SQLite silently drops.
@Index(
    name = "uq_user_data_imports_active",
    definition =
        "create unique index uq_user_data_imports_active on user_data_imports (user_id) " +
            "where state in ('AWAITING_ARCHIVE','PENDING','RUNNING')",
)
// Serves the listing and the two sweeps, which all lead on one of the two columns.
@Index(name = "ix_user_data_imports_user_state", columnNames = ["user_id", "state"])
class UserDataImportModel(
    id: UUID,
    // No index of Ebean's: ix_user_data_imports_user_state leads with user_id.
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE, noIndex = true) var user: UserModel,
    var state: String,
    var requestedAt: Instant,
    var lastActivityAt: Instant,
    var taskId: UUID? = null,
    var runToken: UUID? = null,
    var uploadedBytes: Long = 0,
    var archiveCompletedAt: Instant? = null,
    var startedAt: Instant? = null,
    var completedAt: Instant? = null,
    var archiveHeld: Boolean = false,
    var byteSize: Long? = null,
    var formatVersion: Int? = null,
    var announcedPins: Int? = null,
    var createdPins: Int = 0,
    var skippedPins: Int = 0,
    var createdBoards: Int = 0,
    var skippedBoards: Int = 0,
    var createdTags: Int = 0,
    var skippedTags: Int = 0,
    var issueCount: Int = 0,
    var issueDetailTruncated: Boolean = false,
    var failureCode: String? = null,
) : BaseModel(id = id)
