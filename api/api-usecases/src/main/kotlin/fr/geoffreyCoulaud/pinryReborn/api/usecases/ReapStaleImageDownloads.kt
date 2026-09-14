package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import java.time.Duration

/**
 * Bounds `image_download`, whose rows are otherwise immortal: a PENDING row whose task is terminal
 * or gone becomes FAILED, which stops the client's polling, and past [failedGrace] it is deleted.
 */
class ReapStaleImageDownloads(
    private val imageDownloadRepository: ImageDownloadRepositoryInterface,
    private val taskQueue: TaskQueueInterface,
    private val clock: Clock,
    private val failedGrace: Duration,
) {
    /** Returns the rows touched, settled plus deleted, for the eventual metrics surface. */
    fun reap(): Int {
        val now = clock.now()
        val pending = imageDownloadRepository.findPending()
        val live = taskQueue.findLiveIds(pending.map { it.taskId })
        // markFailed is a CAS on PENDING, so a row the worker settles between the read and this
        // write is refused rather than overwritten, and is not counted.
        val settled = pending
            .filterNot { it.taskId in live }
            .count { imageDownloadRepository.markFailed(it.pinId, DownloadReason.INTERNAL_ERROR, now) }
        return settled + imageDownloadRepository.deleteFailedBefore(now - failedGrace)
    }
}
