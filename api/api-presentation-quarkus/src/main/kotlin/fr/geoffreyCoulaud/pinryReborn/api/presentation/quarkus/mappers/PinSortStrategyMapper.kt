package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinSortStrategyInputEnum

object PinSortStrategyMapper {
    fun PinSortStrategyInputEnum.toDomain(): PinSortStrategy =
        when (this) {
            PinSortStrategyInputEnum.CREATED_AT_ASC -> PinSortStrategy.CREATED_AT_ASC
            PinSortStrategyInputEnum.CREATED_AT_DESC -> PinSortStrategy.CREATED_AT_DESC
        }
}
