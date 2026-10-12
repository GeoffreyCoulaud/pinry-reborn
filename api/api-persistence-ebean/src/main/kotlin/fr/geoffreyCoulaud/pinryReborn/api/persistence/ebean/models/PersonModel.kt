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
@Table(name = "persons")
// A name and its addresses together are an identity per author, the name folded as a tag's is.
// The addresses by their digest: 20 of 2000 characters pass an index entry's 2704 bytes (ADR 0059).
@Index(
    name = "ux_persons_author_name_urls",
    definition = "create unique index ux_persons_author_name_urls on persons (author_id, name, md5(urls))",
)
class PersonModel(
    id: UUID,
    author: UserModel,
    @Column(columnDefinition = Collations.CASE_INSENSITIVE_TEXT) val name: String,
    // PersonModelMapper.canonicalUrls: a JSON array held as text, SQLite having no array type.
    val urls: String,
    createdAt: Instant,
) : AuthoredBaseModel(id = id, author = author, createdAt = createdAt)
