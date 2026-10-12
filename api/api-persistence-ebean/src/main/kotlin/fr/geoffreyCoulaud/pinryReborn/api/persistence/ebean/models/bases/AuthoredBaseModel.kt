package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.UserModel
import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@Suppress("AbstractClassCanBeConcreteClass") // Abstract by intent: a mapped-superclass, never instantiated directly.
abstract class AuthoredBaseModel(
    id: UUID,
    // No index of Ebean's: each table declares one leading with author_id, which MigratedSchemaTest holds.
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.CASCADE, noIndex = true) var author: UserModel,
    // Written by the mapper from the domain entity, never generated: the use case that creates it owns the instant.
    var createdAt: Instant,
) : BaseModel(id = id)
