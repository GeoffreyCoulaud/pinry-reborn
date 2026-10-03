package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StorageLayout
import jakarta.enterprise.context.ApplicationScoped
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

/** A probed original whose row is built and whose bytes are still staged. */
data class IngestedMedia(val media: Media, val staged: StagedFile)

/** What this instance hosts, read from `media.*` by the composition root. */
data class MediaBounds(
    val maxImageBytes: Long,
    val maxVideoBytes: Long,
    val maxVideoDuration: Duration,
    val maxPixels: Long,
) {
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
    private val videoProcessor: VideoProcessor,
    private val bounds: MediaBounds,
) {
    fun stage(source: InputStream): StagedFile = mediaStore.stage(source, bounds.maxStagedBytes)

    fun digest(source: InputStream): String = mediaStore.digest(source, bounds.maxStagedBytes)

    fun ingest(staged: StagedFile, ownerId: UUID, pinId: UUID, createdAt: Instant): IngestedMedia =
        ingest(staged, ownerId, pinId, createdAt, repackage = true)

    /**
     * An export's video is stored as the archive carries it: it was repackaged when first ingested, and a second
     * pass would change its bytes (decision iii).
     */
    fun ingestArchived(staged: StagedFile, ownerId: UUID, pinId: UUID, createdAt: Instant): IngestedMedia =
        ingest(staged, ownerId, pinId, createdAt, repackage = false)

    /** Whatever a probe, a bound or the repackaging throws, the staged file is discarded first. */
    @Suppress("TooGenericExceptionCaught")
    private fun ingest(
        staged: StagedFile,
        ownerId: UUID,
        pinId: UUID,
        createdAt: Instant,
        repackage: Boolean,
    ): IngestedMedia {
        val found =
            try {
                identify(staged, repackage)
            } catch (e: Exception) {
                mediaStore.discardQuietly(staged)
                throw e
            }
        val mediaId = randomUUID()
        val storageKey = "${StorageLayout.ORIGINALS_DIRECTORY}/$ownerId/$pinId/$mediaId.${found.extension}"
        val media =
            Media(
                id = mediaId, pinId = pinId, mimeType = found.mimeType, width = found.width,
                height = found.height, animated = found.animated, byteSize = found.stored.byteSize,
                contentHash = found.stored.contentHash, storageKey = storageKey, createdAt = createdAt,
            )
        return IngestedMedia(media, found.stored)
    }

    /** libvips first, ffprobe for what libvips cannot store (decision ii). */
    private fun identify(staged: StagedFile, repackage: Boolean): Found {
        val imageRefusal: ImageProbeException
        try {
            val image = imageProbe.probe(staged, bounds.maxPixels)
            refuseOver(staged, bounds.maxImageBytes)
            val format = image.format
            return Found(format.mimeType, format.extension, image.width, image.height, image.animated, staged)
        } catch (e: UndecodableImageException) {
            imageRefusal = e
        } catch (e: UnsupportedImageFormatException) {
            imageRefusal = e
        }
        return video(staged, imageRefusal, repackage)
    }

    /** A file ffprobe cannot read either keeps libvips' refusal, so an AVIF stays an unsupported format. */
    private fun video(staged: StagedFile, imageRefusal: ImageProbeException, repackage: Boolean): Found {
        val video =
            try {
                videoProcessor.probe(staged, bounds.maxVideoDuration)
            } catch (ignored: UndecodableVideoException) {
                throw imageRefusal
            }
        refuseOver(staged, bounds.maxVideoBytes)
        val container = VideoContainer.of(video.videoCodec, video.audioCodec)
        val stored =
            if (repackage) {
                videoProcessor.repackage(staged, video).also { mediaStore.discardQuietly(staged) }
            } else {
                staged
            }
        val mimeType = "${container.mimeType}; codecs=\"${video.codecs}\""
        return Found(mimeType, container.extension, video.width, video.height, animated = true, stored)
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

    /** What a probe found, and the staged file that holds the bytes to store. */
    private class Found(
        val mimeType: String,
        val extension: String,
        val width: Int,
        val height: Int,
        val animated: Boolean,
        val stored: StagedFile,
    )
}
