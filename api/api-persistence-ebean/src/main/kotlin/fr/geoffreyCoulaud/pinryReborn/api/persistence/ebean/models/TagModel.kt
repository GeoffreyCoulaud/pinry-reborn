package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.AuthoredBaseModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.Collations
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tags")
// A tag name is an identity per author.
@Index(
    name = "ix_tags_author_name_nocase",
    definition = "create unique index ix_tags_author_name_nocase on tags (author_id, name)",
)
class TagModel(
    id: UUID,
    author: UserModel,
    @Column(columnDefinition = Collations.CASE_INSENSITIVE_TEXT) val name: String,
    createdAt: Instant,
) : AuthoredBaseModel(id = id, author = author, createdAt = createdAt)
