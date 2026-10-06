package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MeasuredMedia
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StorageLayout
import jakarta.enterprise.context.ApplicationScoped
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

/** A probed original whose row is built and whose bytes are still staged. */
data class IngestedMedia(val media: Media, val staged: StagedFile)

/**
 * The only way an original enters storage (ADR 0049, decision 2). Each caller saves the row in its own
 * transaction, so promotion and its undoing are separate steps.
 */
@ApplicationScoped
class MediaIngestion(
    private val mediaStore: MediaStore,
    private val imageProbe: ImageProbe,
    private val videoProcessor: VideoProcessor,
    private val limits: MediaLimits,
) {
    fun stage(source: InputStream): StagedFile = mediaStore.stage(source, limits.maxStagedBytes)

    fun digest(source: InputStream): String = mediaStore.digest(source, limits.maxStagedBytes)

    fun ingest(staged: StagedFile, ownerId: UUID, pinId: UUID, createdAt: Instant): IngestedMedia =
        ingest(staged, ownerId, pinId, createdAt, keepArchivedMp4 = false)

    /**
     * An archived MP4 already repackaged is kept, a second pass changing its bytes; any other video is repackaged,
     * which keeps a stored WebM's bytes (ADR 0049, decision 3).
     */
    fun ingestArchived(staged: StagedFile, ownerId: UUID, pinId: UUID, createdAt: Instant): IngestedMedia =
        ingest(staged, ownerId, pinId, createdAt, keepArchivedMp4 = true)

    /** Whatever a probe, a bound or the repackaging throws, the staged file is discarded first. */
    @Suppress("TooGenericExceptionCaught")
    private fun ingest(
        staged: StagedFile,
        ownerId: UUID,
        pinId: UUID,
        createdAt: Instant,
        keepArchivedMp4: Boolean,
    ): IngestedMedia {
        val found =
            try {
                identify(staged, keepArchivedMp4)
            } catch (e: Exception) {
                mediaStore.discardQuietly(staged)
                throw e
            }
        val mediaId = randomUUID()
        val storageKey = "${StorageLayout.ORIGINALS_DIRECTORY}/$ownerId/$pinId/$mediaId.${found.extension}"
        val measured = found.measured
        val stored = found.stored
        val media =
            when (measured) {
                is VideoProbeResult -> Media.Video(
                    id = mediaId, pinId = pinId, mimeType = found.mimeType, width = measured.width,
                    height = measured.height, byteSize = stored.byteSize, contentHash = stored.contentHash,
                    storageKey = storageKey, createdAt = createdAt, frames = measured.frames,
                    duration = measured.duration, videoBitRate = measured.videoBitRate, sound = measured.sound,
                )
                is ProbeResult -> if (measured.animated) {
                    Media.AnimatedImage(
                        id = mediaId, pinId = pinId, mimeType = found.mimeType, width = measured.width,
                        height = measured.height, byteSize = stored.byteSize, contentHash = stored.contentHash,
                        storageKey = storageKey, createdAt = createdAt, frames = measured.frames,
                    )
                } else {
                    Media.StillImage(
                        id = mediaId, pinId = pinId, mimeType = found.mimeType, width = measured.width,
                        height = measured.height, byteSize = stored.byteSize, contentHash = stored.contentHash,
                        storageKey = storageKey, createdAt = createdAt,
                    )
                }
            }
        return IngestedMedia(media, stored)
    }

    private fun identify(staged: StagedFile, keepArchivedMp4: Boolean): Found {
        val measured = measure(staged)
        limits.refuseIfOver(measured)
        return when (measured) {
            is ProbeResult -> {
                val format = measured.format
                Found(format.mimeType, format.extension, measured, staged)
            }
            is VideoProbeResult -> video(staged, measured, keepArchivedMp4)
        }
    }

    /** libvips first, ffprobe for what libvips cannot store (ADR 0049, decision 2). */
    private fun measure(staged: StagedFile): MeasuredMedia {
        val imageRefusal: ImageProbeException
        try {
            return imageProbe.probe(staged)
        } catch (e: UndecodableImageException) {
            imageRefusal = e
        } catch (e: UnsupportedImageFormatException) {
            imageRefusal = e
        }
        // A file ffprobe cannot read either keeps libvips' refusal, so an AVIF stays an unsupported format.
        try {
            return videoProcessor.probe(staged, limits.maxVideoDuration)
        } catch (ignored: UndecodableVideoException) {
            throw imageRefusal
        }
    }

    private fun video(staged: StagedFile, video: VideoProbeResult, keepArchivedMp4: Boolean): Found {
        val container = VideoContainer.of(video.videoCodec, video.audioCodec)
        // An archived MP4 alone is kept: repackaged again it would change, where a WebM keeps its bytes and one
        // demuxer reads both WebM and Matroska, so a file in any other container is made the one its codecs choose.
        val keptAsArchived =
            keepArchivedMp4 && video.alreadyRepackaged &&
                video.demuxedAs == VideoContainer.MP4 && container == VideoContainer.MP4
        val stored =
            if (keptAsArchived) {
                staged
            } else {
                videoProcessor.repackage(staged, video).also { mediaStore.discardQuietly(staged) }
            }
        val mimeType = "${container.mimeType}; codecs=\"${video.codecs}\""
        return Found(mimeType, container.extension, video, stored)
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
        val measured: MeasuredMedia,
        val stored: StagedFile,
    )
}
