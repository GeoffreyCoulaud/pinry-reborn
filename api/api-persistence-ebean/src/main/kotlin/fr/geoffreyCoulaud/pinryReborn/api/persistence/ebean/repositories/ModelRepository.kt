package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.BaseModel

internal class ModelRepository<T : BaseModel>(private val persistor: Persistor) {
    fun saveAndReturn(model: T): T = model.also { persistor.merge(it) }
}
