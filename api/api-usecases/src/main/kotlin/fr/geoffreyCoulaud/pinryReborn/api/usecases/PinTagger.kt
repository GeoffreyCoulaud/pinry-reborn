package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinTaggingPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinTaggingPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinTaggingSoftDeletedPinError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PinTagger(
    private val tagCreator: TagCreator,
    private val pinRepository: PinRepositoryInterface,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    fun setTags(
        pinId: UUID,
        tagNames: List<String>,
        user: User,
    ): Pin {
        val pin = pinRepository.findPinById(id = pinId) ?: throw PinTaggingPinDoesNotExistError()
        if (pin.author != user) throw PinTaggingPermissionError()
        if (pin.softDeletedAt != null) throw PinTaggingSoftDeletedPinError()

        val tags = resolveTags(tagNames = tagNames, user = user)
        // The fence re-reads the pin, so a recycling or a setBoards landed since the read is kept, not restored.
        return pinRepository.saveFenced(transactionRunner, pinId, held = ::activeOrRefused) {
            it.copy(tags = tags, updatedAt = clock.now())
        } ?: throw PinTaggingPinDoesNotExistError()
    }

    /** The resolution half, outside any transaction, so [PinUpdater] runs it before opening its own. */
    fun resolveTags(tagNames: List<String>, user: User): List<Tag> =
        tagNames.map { tagCreator.findOrCreate(name = it, user = user) }

    private fun activeOrRefused(pin: Pin): Boolean {
        if (pin.softDeletedAt != null) throw PinTaggingSoftDeletedPinError()
        return true
    }
}
