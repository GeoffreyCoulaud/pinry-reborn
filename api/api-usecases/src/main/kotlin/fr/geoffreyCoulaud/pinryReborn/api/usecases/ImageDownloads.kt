package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImageDownloadDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImageDownloadInProgressError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * What a requester still has to watch or to clear: a row lives while the fetch runs and after it
 * failed, success deleting it (spec `docs/specs/2026-09-10-web-application.md`, section 4.4).
 */
@ApplicationScoped
class ImageDownloads(
    private val imageDownloadRepository: ImageDownloadRepositoryInterface,
    private val transactionRunner: TransactionRunner,
) {
    fun list(requester: User, cursor: Cursor?, pageSize: Int): Page<ImageDownload> =
        imageDownloadRepository.findByAuthor(requester.id, cursor, pageSize)

    /**
     * Drops one settled row: what the requester cannot see is absent, and a running row belongs to
     * the worker. One transaction, the same download being requestable again while this runs.
     */
    fun delete(requester: User, pinId: UUID): Unit = transactionRunner.inTransaction {
        val download = imageDownloadRepository.findByAuthorAndPin(requester.id, pinId)
            ?: throw ImageDownloadDoesNotExistError()
        if (download.status == DownloadStatus.PENDING) throw ImageDownloadInProgressError()
        imageDownloadRepository.deleteByPinId(pinId)
    }
}
