package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.ReapExpiredTasks
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
        // Chunked because findLiveIds spends one host parameter per id: past SQLite's ceiling the
        // statement throws, safeAll logs it, and the sweep that bounds this table stops bounding it.
        val live = pending.chunked(LOOKUP_BATCH_SIZE).flatMapTo(mutableSetOf()) { batch ->
            taskQueue.findLiveIds(batch.map { it.taskId })
        }
        // markFailed is a CAS on PENDING, so a row the worker settles between the read and this
        // write is refused rather than overwritten, and is not counted.
        val settled = pending
            .filterNot { it.taskId in live }
            .count { imageDownloadRepository.markFailed(it.pinId, DownloadReason.INTERNAL_ERROR, now) }
        return settled + imageDownloadRepository.deleteFailedBefore(now - failedGrace)
    }

    private companion object {
        /** A constant, not a key, for [ReapExpiredTasks.REAP_BATCH_SIZE]'s reason: the bound is the driver's. */
        const val LOOKUP_BATCH_SIZE = 500
    }
}
