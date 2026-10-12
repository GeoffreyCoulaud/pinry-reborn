package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.AuthoredBaseModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.Collations
import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "pins")
// A key's column leads an index, or the deletion of its parent scans the table.
@Index(name = "ix_pins_author", columnNames = ["author_id"])
@Index(name = "ix_pins_publisher", columnNames = ["publisher_id"])
@Suppress("LongParameterList") // Ebean entity: every parameter is a persisted column.
class PinModel(
    id: UUID,
    author: UserModel,
    var sourceContextUrl: String?,
    var sourceMediaUrl: String?,
    @Column(columnDefinition = Collations.CASE_INSENSITIVE_TEXT) var description: String,
    createdAt: Instant,
    // Written by the mapper from the domain entity, never generated. See AuthoredBaseModel.
    var updatedAt: Instant,
    override var softDeletedAt: Instant? = null,
    // A person's deletion removes their credits, never the pins (ADR 0058, decision 2).
    @ManyToOne @DbForeignKey(onDelete = ConstraintMode.SET_NULL) var publisher: PersonModel? = null,
    var publishedAt: Instant? = null,
) :
    AuthoredBaseModel(
        id = id,
        author = author,
        createdAt = createdAt,
    ),
    SoftDeletableModel
