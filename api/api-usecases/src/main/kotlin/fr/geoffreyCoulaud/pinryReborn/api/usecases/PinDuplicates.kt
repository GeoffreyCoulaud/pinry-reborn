package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** A pin's likely duplicate: the other pin, and whether the user said it is not one. */
data class PinDuplicate(val pin: Pin, val rejected: Boolean)

/** The pairs the worker found, as the user reads them (ADR 0051, decision 7). */
@ApplicationScoped
class PinDuplicates(
    private val duplicateRepository: PinDuplicateRepositoryInterface,
    private val pinRepository: PinRepositoryInterface,
    private val pinGetter: PinGetter,
) {
    /** The pins among [pins] with a pending duplicate, in one read; no permission check, as `statesFor`. */
    fun pendingAmong(pins: Collection<Pin>): Set<UUID> = duplicateRepository.findPinIdsWithPending(pins.map { it.id })

    /** [pinId]'s duplicates, oldest first; none while the pin is recycled, which hides its pairs. */
    fun list(pinId: UUID, user: User): List<PinDuplicate> {
        pinGetter.getPinForUser(reader = user, pinId = pinId)
        val rejectedByPin = duplicateRepository.findShownFor(pinId)
        return pinRepository
            .findPinsByIds(rejectedByPin.keys.toList())
            .sortedBy { it.createdAt }
            .map { PinDuplicate(it, rejectedByPin.getValue(it.id)) }
    }
}
