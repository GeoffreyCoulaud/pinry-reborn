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

@ApplicationScoped
class PinUpdater(
    private val pinTagger: PinTagger,
    private val pinBoardSetter: PinBoardSetter,
    private val pinRepository: PinRepositoryInterface,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    /** Replaces every field the caller sends, in one transaction (ADR 0038). */
    @Suppress("LongParameterList", "ThrowsCount") // The whole pin, and four guard clauses.
    fun update(
        pinId: UUID,
        description: String,
        sourceContextUrl: String?,
        sourceMediaUrl: String?,
        tagNames: List<String>,
        boardIds: List<UUID>,
        user: User,
    ): Pin = transactionRunner.inTransaction {
        val pin = pinRepository.findPinById(id = pinId) ?: throw PinUpdatePinDoesNotExistError()
        if (pin.author != user) throw PinUpdatePermissionError()
        if (pin.softDeletedAt != null) throw PinUpdateSoftDeletedPinError()

        // A tag this invents is rolled back with the rest when a later board refuses the whole write.
        val tags = pinTagger.resolveTags(tagNames = tagNames, user = user)
        val boards = pinBoardSetter.resolveBoards(boardIds = boardIds, user = user)
        // The fence re-reads the pin, so a recycling landed since the read is kept, not restored.
        pinRepository.saveFenced(transactionRunner, pinId, held = ::activeOrRefused) {
            it.copy(
                description = description,
                sourceContextUrl = sourceContextUrl,
                sourceMediaUrl = sourceMediaUrl,
                tags = tags,
                boards = boards,
                updatedAt = clock.now(),
            )
        } ?: throw PinUpdatePinDoesNotExistError()
    }

    private fun activeOrRefused(pin: Pin): Boolean {
        if (pin.softDeletedAt != null) throw PinUpdateSoftDeletedPinError()
        return true
    }
}
