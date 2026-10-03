package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StorageLayout
import jakarta.enterprise.context.ApplicationScoped
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

/** A probed original whose row is built and whose bytes are still staged. */
data class IngestedMedia(val media: Media, val staged: StagedFile)

/** What this instance hosts, read from `media.*` by the composition root. */
data class MediaBounds(val maxImageBytes: Long, val maxVideoBytes: Long, val maxPixels: Long) {
    /** Staging precedes the probe, so it admits the larger bound and the probe's answer applies its own. */
    val maxStagedBytes: Long get() = maxOf(maxImageBytes, maxVideoBytes)
}

/**
 * The only way an original enters storage (ADR 0049, decision 2). Each caller saves the row in its own
 * transaction, so promotion and its undoing are separate steps.
 */
@ApplicationScoped
class MediaIngestion(
    private val mediaStore: MediaStore,
    private val imageProbe: ImageProbe,
    private val bounds: MediaBounds,
) {
    fun stage(source: InputStream): StagedFile = mediaStore.stage(source, bounds.maxStagedBytes)

    fun digest(source: InputStream): String = mediaStore.digest(source, bounds.maxStagedBytes)

    /** Whatever the probe or the bound throws, the staged file is discarded first. */
    @Suppress("TooGenericExceptionCaught")
    fun ingest(staged: StagedFile, ownerId: UUID, pinId: UUID, createdAt: Instant): IngestedMedia {
        val probe =
            try {
                imageProbe.probe(staged, bounds.maxPixels).also { refuseOver(staged, bounds.maxImageBytes) }
            } catch (e: Exception) {
                mediaStore.discardQuietly(staged)
                throw e
            }
        val mediaId = randomUUID()
        val storageKey = "${StorageLayout.ORIGINALS_DIRECTORY}/$ownerId/$pinId/$mediaId.${probe.format.extension}"
        val media =
            Media(
                id = mediaId, pinId = pinId, mimeType = probe.format.mimeType, width = probe.width,
                height = probe.height, animated = probe.animated, byteSize = staged.byteSize,
                contentHash = staged.contentHash, storageKey = storageKey, createdAt = createdAt,
            )
        return IngestedMedia(media, staged)
    }

    private fun refuseOver(staged: StagedFile, maxBytes: Long) {
        if (staged.byteSize > maxBytes) throw MediaTooLargeException("${staged.byteSize} bytes, past $maxBytes")
    }

    fun promote(ingested: IngestedMedia) = mediaStore.promote(ingested.staged, ingested.media.storageKey)

    /** Best-effort undoing of [promote], or of what preceded it: neither copy may stay behind. */
    fun discard(ingested: IngestedMedia) {
        mediaStore.discardQuietly(ingested.staged)
        mediaStore.deleteQuietly(ingested.media.storageKey)
    }
}
