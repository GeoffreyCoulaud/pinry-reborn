package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DuplicateDecision
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.DuplicateDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.DuplicateResolutionInvalidError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdatePinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinUpdateSoftDeletedPinError
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID

/** Applies the user's decision over the open pin's group of duplicates (ADR 0052). */
@ApplicationScoped
class DuplicateResolver(
    private val pinRepository: PinRepositoryInterface,
    private val duplicateRepository: PinDuplicateRepositoryInterface,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    /** All or nothing: every pin is resolved before the first write (ADR 0039, decision 2); answers the kept pin. */
    @Suppress("ThrowsCount") // The open pin's three refusals, then the list's.
    fun resolve(pinId: UUID, decisions: Map<UUID, DuplicateDecision>, user: User): Pin {
        val keptId = decisions.filterValues { it == DuplicateDecision.KEEP }.keys.singleOrNull()
        val openHeld = decisions[pinId] in setOf(DuplicateDecision.KEEP, DuplicateDecision.MERGE)
        if (keptId == null || !openHeld || decisions.size < 2) throw DuplicateResolutionInvalidError()
        return transactionRunner.inTransaction {
            val found = pinRepository.findPinsByIds(decisions.keys.toList()).associateBy { it.id }
            val open = found[pinId] ?: throw PinUpdatePinDoesNotExistError()
            if (open.author != user) throw PinUpdatePermissionError()
            if (open.softDeletedAt != null) throw PinUpdateSoftDeletedPinError()
            if (!duplicateRepository.findShownFor(pinId).keys.containsAll(decisions.keys - pinId)) {
                throw DuplicateDoesNotExistError()
            }
            val now = clock.now()
            val (rejected, held) = decisions.keys.partition { decisions[it] == DuplicateDecision.REJECT }
            // Before any pin is recycled, so it is not the kept pin's candidate afterwards (ADR 0052, decision 5).
            rejected.forEach { rejectedId -> held.forEach { duplicateRepository.setRejected(rejectedId, it, now) } }
            val absorbed = held.filter { decisions[it] == DuplicateDecision.MERGE }.map(found::getValue)
            absorb(found.getValue(keptId), absorbed.sortedBy { it.createdAt }, now)
        }
    }

    /** [kept] gains what [absorbed] hold, its blank fields filling in [absorbed]'s order (ADR 0051, decision 8). */
    @Suppress("RowMergedOutsideTransaction") // [resolve] reads [kept] in the transaction it calls this from.
    private fun absorb(kept: Pin, absorbed: List<Pin>, now: Instant): Pin {
        if (absorbed.isEmpty()) return kept
        val merged = pinRepository.savePin(
            kept.copy(
                description = kept.description.ifBlank {
                    absorbed.map { it.description }.firstOrNull { it.isNotBlank() } ?: kept.description
                },
                sourceContextUrl = kept.sourceContextUrl ?: absorbed.firstNotNullOfOrNull { it.sourceContextUrl },
                tags = (kept.tags + absorbed.flatMap { it.tags }).distinct(),
                boards = (kept.boards + absorbed.flatMap { it.boards }).distinct(),
                updatedAt = now,
            ),
        )
        // Their pairs stay theirs, hidden while they are recycled: the kept media was not measured against them.
        pinRepository.softDeletePins(pinIds = absorbed.map { it.id }, at = now)
        return merged
    }
}
