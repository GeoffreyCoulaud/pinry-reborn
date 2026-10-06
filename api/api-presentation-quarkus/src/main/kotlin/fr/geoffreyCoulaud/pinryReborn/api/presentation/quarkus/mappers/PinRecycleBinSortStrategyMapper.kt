package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinRecycleBinSortStrategyInputEnum

object PinRecycleBinSortStrategyMapper {
    fun PinRecycleBinSortStrategyInputEnum.toDomain(): PinSortStrategy =
        when (this) {
            PinRecycleBinSortStrategyInputEnum.CREATED_AT_ASC -> PinSortStrategy.CREATED_AT_ASC
            PinRecycleBinSortStrategyInputEnum.CREATED_AT_DESC -> PinSortStrategy.CREATED_AT_DESC
            PinRecycleBinSortStrategyInputEnum.DELETED_AT_DESC -> PinSortStrategy.DELETED_AT_DESC
        }
}
