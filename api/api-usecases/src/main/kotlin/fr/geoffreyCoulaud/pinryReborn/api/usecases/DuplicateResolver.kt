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
            rejected.forEach { duplicateRepository.setRejected(it, held, now) }
            val absorbed =
                held.filter { decisions[it] == DuplicateDecision.MERGE }.map(found::getValue).sortedBy { it.createdAt }
            val kept = found.getValue(keptId)
            if (absorbed.isEmpty()) {
                kept
            } else {
                val merged = pinRepository.savePin(afterAbsorbing(kept, absorbed, now))
                // Their pairs stay theirs, hidden while recycled: the kept media was not measured against them.
                pinRepository.softDeletePins(pinIds = absorbed.map { it.id }, at = now)
                merged
            }
        }
    }

    /** [kept] with what [absorbed] hold, blanks filled in [absorbed]'s order, oldest first (ADR 0052, decision 3). */
    private fun afterAbsorbing(kept: Pin, absorbed: List<Pin>, now: Instant): Pin =
        kept.copy(
            description =
                kept.description.ifBlank {
                    absorbed.map { it.description }.firstOrNull { it.isNotBlank() } ?: kept.description
                },
            sourceContextUrl = kept.sourceContextUrl ?: absorbed.firstNotNullOfOrNull { it.sourceContextUrl },
            tags = (kept.tags + absorbed.flatMap { it.tags }).distinct(),
            boards = (kept.boards + absorbed.flatMap { it.boards }).distinct(),
            updatedAt = now,
        )
}
