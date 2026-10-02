package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.io.InputStream
import java.time.Instant

interface MediaStore {
    /**
     * Stream [source] into a fresh temp file under the data dir, aborting past [maxBytes]
     * (throws MediaTooLargeException), computing byteSize + SHA-256 in one pass.
     */
    fun stage(source: InputStream, maxBytes: Long): StagedFile

    /**
     * SHA-256 of [source], hex encoded, read without writing anything, aborting past [maxBytes]
     * (throws MediaTooLargeException). Asked before staging: bytes already held then cost nothing.
     */
    fun digest(source: InputStream, maxBytes: Long): String

    /**
     * Move a staged temp file to [storageKey] (a fresh path).
     */
    fun promote(staged: StagedFile, storageKey: String)

    /**
     * Open a read stream for a stored key.
     */
    fun openStream(storageKey: String): InputStream

    /**
     * Delete [storageKey] if present (idempotent).
     */
    fun delete(storageKey: String)

    /**
     * Delete a staged temp file (cleanup on failure; idempotent).
     */
    fun discard(staged: StagedFile)

    /** Delete every staged file last modified before [olderThan], returning how many. */
    fun discardOrphanedStagedFiles(olderThan: Instant): Int

    /**
     * Loan to [block] the storage key of every original last modified before [olderThan], with the
     * loan contract of [fr.geoffreyCoulaud.pinryReborn.api.domain.exports.ExportArchiveStore.forEachStorageKeyOnDisk].
     */
    fun forEachStorageKeyOnDisk(olderThan: Instant, block: (Sequence<String>) -> Unit)
}
