package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdateSoftDeletedPinError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** Merges duplicates into one pin, which keeps its media and sources (ADR 0051, decision 8). */
@ApplicationScoped
class PinMerger(
    private val pinRepository: PinRepositoryInterface,
    private val duplicateResolver: DuplicateResolver,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    /** All or nothing: every pin is resolved before the first write (ADR 0039, decision 2). */
    fun merge(keptPinId: UUID, absorbedPinIds: List<UUID>, user: User): Pin = transactionRunner.inTransaction {
        val found = pinRepository.findPinsByIds(listOf(keptPinId) + absorbedPinIds).associateBy { it.id }
        val kept = accepted(found[keptPinId], user)
        val absorbed = absorbedPinIds.map { accepted(found[it], user) }
        duplicateResolver.absorb(kept, absorbed, clock.now())
    }

    @Suppress("ThrowsCount") // The three refusals a pin earns, wherever it was named.
    private fun accepted(pin: Pin?, user: User): Pin {
        if (pin == null) throw PinUpdatePinDoesNotExistError()
        if (pin.author != user) throw PinUpdatePermissionError()
        if (pin.softDeletedAt != null) throw PinUpdateSoftDeletedPinError()
        return pin
    }
}
