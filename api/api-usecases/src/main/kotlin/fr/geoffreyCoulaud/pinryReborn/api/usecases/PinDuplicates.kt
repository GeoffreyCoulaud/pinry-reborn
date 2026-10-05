package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.DuplicateDoesNotExistError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** A pin's likely duplicate: the other pin, and whether the user said it is not one. */
data class PinDuplicate(val pin: Pin, val rejected: Boolean)

/** The pairs the worker found, as the user reads and rejects them (ADR 0051, decisions 7 and 9). */
@ApplicationScoped
class PinDuplicates(
    private val duplicateRepository: PinDuplicateRepositoryInterface,
    private val pinRepository: PinRepositoryInterface,
    private val pinGetter: PinGetter,
    private val clock: Clock,
) {
    /** The pins among [pins] with a pending duplicate, in one read; no permission check, as `statesFor`. */
    fun pendingAmong(pins: Collection<Pin>): Set<UUID> =
        duplicateRepository.findPinIdsWithPending(pins.map { it.id })

    /** [pinId]'s duplicates, oldest first; none while the pin is recycled, which hides its pairs. */
    fun list(pinId: UUID, user: User): List<PinDuplicate> {
        pinGetter.getPinForUser(reader = user, pinId = pinId)
        val rejectedByPin = duplicateRepository.findShownFor(pinId)
        return pinRepository.findPinsByIds(rejectedByPin.keys.toList())
            .sortedBy { it.createdAt }
            .map { PinDuplicate(it, rejectedByPin.getValue(it.id)) }
    }

    fun setRejected(pinId: UUID, otherPinId: UUID, rejected: Boolean, user: User): PinDuplicate {
        pinGetter.getPinForUser(reader = user, pinId = pinId)
        val other = pinRepository.findPinById(otherPinId) ?: throw DuplicateDoesNotExistError()
        val rejectedAt = if (rejected) clock.now() else null
        if (!duplicateRepository.setRejected(pinId, otherPinId, rejectedAt)) throw DuplicateDoesNotExistError()
        return PinDuplicate(other, rejected)
    }
}
