package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class DeletePinMedia(
    private val pinRepository: PinRepositoryInterface,
    private val mediaRepository: MediaRepositoryInterface,
    private val mediaStore: MediaStore,
    private val clearPinDownload: ClearPinDownload,
    private val renditionCache: RenditionCache,
) {
    fun delete(pinId: UUID, requester: User) {
        val pin = pinRepository.findPinById(pinId) ?: throw MediaPinDoesNotExistError()
        if (pin.author.id != requester.id) throw MediaPermissionError()
        val media = mediaRepository.findByPinId(pinId)
        if (media != null) {
            mediaRepository.deleteByPinId(pinId)
            mediaStore.deleteQuietly(media.storageKey)
            renditionCache.evictMediaQuietly(media.id)
            clearPinDownload.clear(pinId)
            return
        }
        // No image row: a DELETE during a fetch must still cancel the in-flight/failed download and
        // leave nothing pending (spec section 7). Only when there is nothing at all to remove is this a 404.
        if (!clearPinDownload.clear(pinId)) throw MediaDoesNotExistError()
    }
}
