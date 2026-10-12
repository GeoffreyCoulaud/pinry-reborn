package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.Table

// Never compiled: ModelsPackageArchTest asserts its index rule flags the index named after another table.
@Entity
@Table(name = "pin_tags")
@Index(name = "ux_pin_tags_pin_tag", columnNames = ["pin_id", "tag_id"])
@Index(name = "ix_pin_tag_model_tag", columnNames = ["tag_id"])
class PinTagModel(var pinId: String, var tagId: String)
