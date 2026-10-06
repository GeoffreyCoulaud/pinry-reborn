package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.CancelTask
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Tears down a pin's mode-B download when a direct upload or an image delete supersedes it: cancel the (possibly
 * in-flight) task best-effort, then drop the download row. A still-running fetch is neutralised by DownloadPinMedia's
 * CAS-on-PENDING swap, which finds no PENDING row.
 */
@ApplicationScoped
class ClearPinDownload(
    private val mediaDownloadRepository: MediaDownloadRepositoryInterface,
    private val cancelTask: CancelTask,
) {
    /**
     * Cancel the pin's (possibly in-flight) mode-B download and drop its row, best-effort. Returns true when a download
     * row existed and was cleared, false when there was nothing to clear.
     */
    fun clear(pinId: UUID): Boolean {
        val download = mediaDownloadRepository.findByPinId(pinId) ?: return false
        runCatching { cancelTask.cancel(download.taskId) }
        mediaDownloadRepository.deleteByPinId(pinId)
        return true
    }
}
