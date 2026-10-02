package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDownloadDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDownloadInProgressError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * What a requester still has to watch or to clear: a row lives while the fetch runs and after it
 * failed, success deleting it (spec `docs/specs/2026-09-10-web-application.md`, section 4.4).
 */
@ApplicationScoped
class MediaDownloads(
    private val mediaDownloadRepository: MediaDownloadRepositoryInterface,
    private val transactionRunner: TransactionRunner,
) {
    /** [pageSize] is clamped as [PinGetter] clamps it: at zero the helper answers an empty page with no cursor. */
    fun list(requester: User, cursor: Cursor?, pageSize: Int): Page<MediaDownload> =
        mediaDownloadRepository.findByAuthor(requester.id, cursor, pageSize.coerceIn(1, PinGetter.MAX_PAGE_SIZE))

    /**
     * Drops one settled row: what the requester cannot see is absent, and a running row belongs to
     * the worker. One transaction, the same download being requestable again while this runs.
     */
    fun delete(requester: User, pinId: UUID): Unit = transactionRunner.inTransaction {
        val download = mediaDownloadRepository.findByAuthorAndPin(requester.id, pinId)
            ?: throw MediaDownloadDoesNotExistError()
        if (download.status == DownloadStatus.PENDING) throw MediaDownloadInProgressError()
        mediaDownloadRepository.deleteByPinId(pinId)
    }
}
