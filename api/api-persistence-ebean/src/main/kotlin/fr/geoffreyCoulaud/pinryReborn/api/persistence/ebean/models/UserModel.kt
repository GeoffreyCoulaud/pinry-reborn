package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.BaseModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.Collations
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
@Index(name = "ix_users_name_nocase", definition = "create unique index ix_users_name_nocase on users (name)")
class UserModel(
    id: UUID,
    @Column(columnDefinition = Collations.CASE_INSENSITIVE_TEXT) var name: String,
    // Written by the mapper from the domain entity, never generated. See AuthoredBaseModel.
    var createdAt: Instant,
    override var softDeletedAt: Instant? = null,
) : BaseModel(id = id), SoftDeletableModel
