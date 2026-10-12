package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.pagination

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.bases.BaseModel

data class ModelCursor<M : BaseModel>(
    val pivot: M,
    val direction: CursorDirection,
)
