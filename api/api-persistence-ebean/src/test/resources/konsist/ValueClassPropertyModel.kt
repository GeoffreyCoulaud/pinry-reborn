package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import jakarta.persistence.Entity

// Never compiled: ModelsPackageArchTest asserts its rule flags this property.
@Entity class ValueClassPropertyModel(var address: HttpUrl?)
