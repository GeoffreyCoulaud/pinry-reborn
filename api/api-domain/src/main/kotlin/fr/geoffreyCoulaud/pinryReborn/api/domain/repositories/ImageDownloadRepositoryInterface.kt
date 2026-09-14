package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import java.time.Instant
import java.util.UUID

interface ImageDownloadRepositoryInterface {
    /** Create-or-replace the pin's download row as PENDING with a fresh sourceUrl + taskId. */
    fun upsertPending(pinId: UUID, sourceUrl: String, taskId: UUID, now: Instant): ImageDownload

    fun findByPinId(pinId: UUID): ImageDownload?

    /**
     * The download rows of [pinIds], keyed by pin id; a pin with no row is absent from the map.
     * One `IN (...)` lookup, bounded by the size of [pinIds] (a page).
     */
    fun findByPinIds(pinIds: Collection<UUID>): Map<UUID, ImageDownload>

    /**
     * The downloads of [authorId]'s pins, newest request first. Ownership is a traversal: the row
     * carries no author, so it is read through the pin, and a recycled pin's row is left out.
     */
    fun findByAuthor(authorId: UUID): List<ImageDownload>

    /**
     * The download of [pinId] when that pin is [authorId]'s and not recycled, else null. The same
     * traversal as [findByAuthor], narrowed to one pin: a single row is not read through a page.
     */
    fun findByAuthorAndPin(authorId: UUID, pinId: UUID): ImageDownload?

    /** CAS on PENDING: set FAILED + reason. Returns true if a PENDING row was updated. */
    fun markFailed(pinId: UUID, reason: DownloadReason, now: Instant): Boolean

    /** CAS on PENDING: record the last transient error, keep PENDING. Returns true if updated. */
    fun recordLastError(pinId: UUID, lastError: String, now: Instant): Boolean

    /** CAS on PENDING: delete the row only if still PENDING. Returns the number of rows deleted (0 or 1). */
    fun deleteIfPending(pinId: UUID): Int

    /** Unconditional delete of the pin's download row (idempotent). */
    fun deleteByPinId(pinId: UUID)

    /**
     * Every PENDING row, with the task id that says whether one is still being advanced. Bounded by
     * the downloads in flight, the sweep below being what keeps an abandoned one from staying here.
     */
    fun findPending(): List<ImageDownload>

    /**
     * Delete the FAILED rows last updated before [cutoff]. Neither sweep read filters on the pin's
     * state, unlike [findByAuthor]: this is about the row, not about what a requester can see.
     */
    fun deleteFailedBefore(cutoff: Instant): Int
}
