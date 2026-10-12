package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel

data class ModelPage<M : BaseModel>(
    val items: List<M>,
    val previousCursor: ModelCursor<M>?,
    val nextCursor: ModelCursor<M>?,
)
