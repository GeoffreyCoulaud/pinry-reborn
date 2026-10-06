package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DuplicateDecision
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.DuplicateDecisionInputEnum

object DuplicateDecisionMapper {
    fun DuplicateDecisionInputEnum.toDomain(): DuplicateDecision =
        when (this) {
            DuplicateDecisionInputEnum.KEEP -> DuplicateDecision.KEEP
            DuplicateDecisionInputEnum.MERGE -> DuplicateDecision.MERGE
            DuplicateDecisionInputEnum.REJECT -> DuplicateDecision.REJECT
        }
}
