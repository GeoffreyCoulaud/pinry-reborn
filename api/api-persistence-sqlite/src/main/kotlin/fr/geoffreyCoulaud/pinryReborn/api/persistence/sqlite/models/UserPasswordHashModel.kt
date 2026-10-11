package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PasswordHashAlgorithm
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_password_hashes")
@Index(
    name = "ix_user_password_hashes_user_created",
    definition =
        "create unique index ix_user_password_hashes_user_created " + "on user_password_hashes (user_id, created_at)",
)
class UserPasswordHashModel(
    @ManyToOne var user: UserModel,
    var hash: String,
    @Enumerated(EnumType.STRING) var algorithm: PasswordHashAlgorithm,
    // Written by the mapper from the domain `createdAt` the use case stamps, never generated (D19).
    var createdAt: Instant,
) : BaseModel()
