package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import jakarta.persistence.Entity
import jakarta.persistence.Table

// Never compiled: ModelsPackageArchTest asserts its table rule flags this model, whose table is not `pin_tags`.
@Entity @Table(name = "pin_tag_model") class PinTagModel(var tagId: String)
