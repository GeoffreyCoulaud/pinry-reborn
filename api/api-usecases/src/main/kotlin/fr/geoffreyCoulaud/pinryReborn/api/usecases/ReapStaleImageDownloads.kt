package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import java.time.Duration

/**
 * Bounds `image_download`, whose rows are otherwise immortal: past [pendingGrace] a row no worker is
 * advancing becomes FAILED, which stops the client's polling, and past [failedGrace] it is deleted.
 */
class ReapStaleImageDownloads(
    private val imageDownloadRepository: ImageDownloadRepositoryInterface,
    private val clock: Clock,
    private val failedGrace: Duration,
    private val pendingGrace: Duration,
) {
    /**
     * Returns the rows touched, settled plus deleted. Neither cutoff is indexed, deliberately: this
     * sweep bounds the table, so its scan is over one grace and not over a growing column.
     */
    fun reap(): Int {
        val now = clock.now()
        val settled =
            imageDownloadRepository.failPendingBefore(now - pendingGrace, DownloadReason.INTERNAL_ERROR, now)
        return settled + imageDownloadRepository.deleteFailedBefore(now - failedGrace)
    }
}
