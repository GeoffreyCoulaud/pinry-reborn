package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.pagination

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.BaseModel

data class ModelPage<M : BaseModel>(
    val items: List<M>,
    val previousCursor: ModelCursor<M>?,
    val nextCursor: ModelCursor<M>?,
)
