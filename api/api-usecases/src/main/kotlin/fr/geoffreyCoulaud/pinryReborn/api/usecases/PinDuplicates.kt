package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** The pairs the worker found, as the user reads them (ADR 0051, decisions 7 and 9). */
@ApplicationScoped
class PinDuplicates(
    private val duplicateRepository: PinDuplicateRepositoryInterface,
) {
    /** The pins among [pins] with a pending duplicate, in one read; no permission check, as `statesFor`. */
    fun pendingAmong(pins: Collection<Pin>): Set<UUID> =
        duplicateRepository.findPinIdsWithPending(pins.map { it.id })
}
