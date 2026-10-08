package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.AuthoredBaseModel
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "remote_collections")
// An address is an identity per author, compared exactly: the server normalises no address.
@Index(
    name = "ux_remote_collections_author_url",
    definition = "create unique index ux_remote_collections_author_url on remote_collections (author_id, url)",
)
@Index(name = "ix_remote_collections_board", columnNames = ["board_id"])
class RemoteCollectionModel(
    id: UUID,
    author: UserModel,
    val url: String,
    val name: String,
    @ManyToOne val board: BoardModel,
    createdAt: Instant,
) : AuthoredBaseModel(id = id, author = author, createdAt = createdAt)
