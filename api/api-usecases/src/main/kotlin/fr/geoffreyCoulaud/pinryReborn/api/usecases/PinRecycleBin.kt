package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.images.ImageStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.images.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageRepositoryInterface
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
    private val imageRepository: ImageRepositoryInterface,
    private val imageStore: ImageStore,
    private val clearPinDownload: ClearPinDownload,
    private val renditionCache: RenditionCache,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    private fun findPinAndValidateOwnership(pinId: UUID, user: User): Pin {
        val pin = pinRepository.findPinById(id = pinId) ?: throw PinDeletionPinDoesNotExistError()
        if (pin.author != user) throw PinDeletionPermissionError()
        return pin
    }

    private fun activeOrRefused(pinId: UUID, user: User): Pin =
        findPinAndValidateOwnership(pinId, user).also {
            if (it.softDeletedAt != null) throw PinDeletionPinAlreadySoftDeletedError()
        }

    private fun recycledOrRefused(pinId: UUID, user: User): Pin =
        findPinAndValidateOwnership(pinId, user).also {
            if (it.softDeletedAt == null) throw PinDeletionPinNotSoftDeletedError()
        }

    fun softDelete(pinId: UUID, user: User): Pin =
        pinRepository.softDeletePin(pin = activeOrRefused(pinId, user), at = clock.now())

    /** All or nothing: every pin is resolved before the first write (ADR 0039, decision 2). */
    fun softDeleteAll(pinIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        val pins = pinIds.map { activeOrRefused(it, user) }
        val at = clock.now()
        pins.forEach { pinRepository.softDeletePin(pin = it, at = at) }
    }

    fun restore(pinId: UUID, user: User): Pin =
        pinRepository.restorePin(pin = recycledOrRefused(pinId, user), at = clock.now())

    /** All or nothing, as [softDeleteAll] is. */
    fun restoreAll(pinIds: List<UUID>, user: User) = transactionRunner.inTransaction {
        val pins = pinIds.map { recycledOrRefused(it, user) }
        val at = clock.now()
        pins.forEach { pinRepository.restorePin(pin = it, at = at) }
    }

    fun permanentlyDelete(pinId: UUID, user: User) {
        val pin = recycledOrRefused(pinId, user)
        clearPinDownload.clear(pin.id)
        val image = imageRepository.findByPinId(pin.id)
        imageRepository.deleteByPinId(pin.id)
        pinRepository.permanentlyDeletePin(pin)
        image?.let {
            imageStore.deleteQuietly(it.storageKey)
            renditionCache.evictImageQuietly(it.id)
        }
    }

    fun emptyRecycleBin(user: User) {
        val pins = pinRepository.findAllSoftDeletedPinsForUser(user)
        val images = pins.mapNotNull { pin ->
            clearPinDownload.clear(pin.id)
            val image = imageRepository.findByPinId(pin.id)
            imageRepository.deleteByPinId(pin.id)
            image
        }
        pinRepository.permanentlyDeleteAllSoftDeletedPinsForUser(user)
        images.forEach {
            imageStore.deleteQuietly(it.storageKey)
            renditionCache.evictImageQuietly(it.id)
        }
    }
}
