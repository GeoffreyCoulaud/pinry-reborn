package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ResolvePinMediaState(
    private val pinRepository: PinRepositoryInterface,
    private val mediaRepository: MediaRepositoryInterface,
    private val mediaDownloadRepository: MediaDownloadRepositoryInterface,
    private val transactionRunner: TransactionRunner,
) {
    fun resolve(pinId: UUID, requester: User): PinMediaState {
        val pin = pinRepository.findPinById(pinId) ?: throw MediaPinDoesNotExistError()
        if (pin.author.id != requester.id) throw MediaPermissionError()
        // One snapshot for both reads: the swap commits the new image and the replacement's removal together.
        return transactionRunner.inTransaction {
            PinMediaState.derive(mediaRepository.findByPinId(pinId), mediaDownloadRepository.findByPinId(pinId))
        }
    }

    /**
     * The state of [pins] keyed by pin id, in two reads whatever the page holds; a pin with neither
     * image nor download is absent. No permission check: the caller's query was reader-scoped.
     */
    fun statesFor(pins: Collection<Pin>): Map<UUID, PinMediaState> {
        if (pins.isEmpty()) return emptyMap()
        val pinIds = pins.map { it.id }
        // Same snapshot as above, for the same reason: a swap must not be read half done.
        return transactionRunner.inTransaction {
            val mediaByPin = mediaRepository.findByPinIds(pinIds)
            val downloads = mediaDownloadRepository.findByPinIds(pinIds)
            pinIds
                .associateWith { PinMediaState.derive(mediaByPin[it], downloads[it]) }
                .filterValues { it.status != PinMediaStatus.NONE }
        }
    }
}
