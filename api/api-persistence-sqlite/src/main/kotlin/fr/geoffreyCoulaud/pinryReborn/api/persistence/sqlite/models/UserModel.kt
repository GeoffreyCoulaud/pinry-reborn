package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
// Only `definition` carries `collate nocase`.
@Index(
    name = "ix_users_name_nocase",
    definition = "create unique index ix_users_name_nocase on users (name collate nocase)",
)
class UserModel(
    id: UUID,
    var name: String,
    // Written by the mapper from the domain entity, never generated. See AuthoredBaseModel.
    var createdAt: Instant,
    override var softDeletedAt: Instant? = null,
) : BaseModel(id = id), SoftDeletableModel
