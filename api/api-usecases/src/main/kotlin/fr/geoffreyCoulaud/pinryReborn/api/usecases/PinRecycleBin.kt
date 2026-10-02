package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPinAlreadySoftDeletedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinDeletionPinNotSoftDeletedError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class PinRecycleBin(
    private val pinRepository: PinRepositoryInterface,
    private val mediaRepository: MediaRepositoryInterface,
    private val mediaStore: MediaStore,
    private val clearPinDownload: ClearPinDownload,
    private val renditionCache: RenditionCache,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    private fun validateOwnership(pin: Pin?, user: User): Pin {
        if (pin == null) throw PinDeletionPinDoesNotExistError()
        if (pin.author != user) throw PinDeletionPermissionError()
        return pin
    }

    private fun activeOrRefused(pin: Pin?, user: User): Pin =
        validateOwnership(pin, user).also {
            if (it.softDeletedAt != null) throw PinDeletionPinAlreadySoftDeletedError()
        }

    private fun recycledOrRefused(pin: Pin?, user: User): Pin =
        validateOwnership(pin, user).also {
            if (it.softDeletedAt == null) throw PinDeletionPinNotSoftDeletedError()
        }

    // One read for the whole list, each pin then refused in the list's order as the single route refuses it.
    private fun resolveAll(pinIds: List<UUID>, validate: (Pin?) -> Pin) {
        val found = pinRepository.findPinsByIds(pinIds).associateBy { it.id }
        pinIds.forEach { validate(found[it]) }
    }

    fun softDelete(pinId: UUID, user: User): Pin =
        pinRepository.softDeletePin(pin = activeOrRefused(pinRepository.findPinById(pinId), user), at = clock.now())

    /** All or nothing: every pin is resolved before the first write (ADR 0039, decision 2). */
    fun softDeleteAll(pinIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        resolveAll(pinIds) { activeOrRefused(it, user) }
        pinRepository.softDeletePins(pinIds = pinIds, at = clock.now())
    }

    fun restore(pinId: UUID, user: User): Pin =
        pinRepository.restorePin(pin = recycledOrRefused(pinRepository.findPinById(pinId), user), at = clock.now())

    /** All or nothing, as [softDeleteAll] is. */
    fun restoreAll(pinIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        resolveAll(pinIds) { recycledOrRefused(it, user) }
        pinRepository.restorePins(pinIds = pinIds, at = clock.now())
    }

    fun permanentlyDelete(pinId: UUID, user: User) {
        val pin = recycledOrRefused(pinRepository.findPinById(pinId), user)
        clearPinDownload.clear(pin.id)
        val media = mediaRepository.findByPinId(pin.id)
        mediaRepository.deleteByPinId(pin.id)
        pinRepository.permanentlyDeletePin(pin)
        media?.let {
            mediaStore.deleteQuietly(it.storageKey)
            renditionCache.evictMediaQuietly(it.id)
        }
    }

    fun emptyRecycleBin(user: User) {
        val pins = pinRepository.findAllSoftDeletedPinsForUser(user)
        val deletedMedia = pins.mapNotNull { pin ->
            clearPinDownload.clear(pin.id)
            val media = mediaRepository.findByPinId(pin.id)
            mediaRepository.deleteByPinId(pin.id)
            media
        }
        pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user)
        deletedMedia.forEach {
            mediaStore.deleteQuietly(it.storageKey)
            renditionCache.evictMediaQuietly(it.id)
        }
    }
}
