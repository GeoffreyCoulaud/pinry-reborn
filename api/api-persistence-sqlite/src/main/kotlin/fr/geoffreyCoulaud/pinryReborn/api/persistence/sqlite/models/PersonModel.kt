package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.AuthoredBaseModel
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "persons")
// A name and its addresses together are an identity per author, the name folded as a tag's is.
@Index(
    name = "ix_persons_author_name_nocase_urls",
    definition =
        "create unique index ix_persons_author_name_nocase_urls on persons (author_id, name collate nocase, urls)",
)
class PersonModel(
    id: UUID,
    author: UserModel,
    val name: String,
    // PersonUrls.joined: the addresses sorted, distinct and line-feed joined, empty for none.
    val urls: String,
    createdAt: Instant,
) : AuthoredBaseModel(id = id, author = author, createdAt = createdAt)
