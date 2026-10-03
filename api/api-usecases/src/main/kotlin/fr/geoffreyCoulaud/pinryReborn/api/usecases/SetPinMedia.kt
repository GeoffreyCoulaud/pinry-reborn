package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaCodecUnsupportedError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaInvalidError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaTooLargeError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaTooLongError
import jakarta.enterprise.context.ApplicationScoped
import java.io.InputStream
import java.util.UUID

/**
 * Result of [SetPinMedia.set]: the persisted canonical image, plus whether it replaced a
 * pre-existing image for the pin (used by the controller to pick 201 vs 200).
 */
data class SetPinMediaResult(val media: Media, val replaced: Boolean)

@ApplicationScoped
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class SetPinMedia(
    private val pinRepository: PinRepositoryInterface,
    private val mediaRepository: MediaRepositoryInterface,
    private val mediaStore: MediaStore,
    private val mediaIngestion: MediaIngestion,
    private val clock: Clock,
    private val clearPinDownload: ClearPinDownload,
    private val renditionCache: RenditionCache,
) {
    fun set(pinId: UUID, requester: User, upload: InputStream): SetPinMediaResult {
        val pin = pinRepository.findPinById(pinId) ?: throw MediaPinDoesNotExistError()
        if (pin.author.id != requester.id) throw MediaPermissionError()

        val staged = try {
            mediaIngestion.stage(upload)
        } catch (e: MediaTooLargeException) {
            throw MediaTooLargeError(e)
        }

        val ingested = try {
            mediaIngestion.ingest(staged, requester.id, pinId, clock.now())
        } catch (e: MediaTooLargeException) {
            throw MediaTooLargeError(e)
        } catch (e: UnsupportedImageFormatException) {
            throw MediaCodecUnsupportedError(e)
        } catch (e: VideoProcessorException) {
            throw refusalOf(e)
        } catch (e: ImageProbeException) {
            // Keep the client-facing message fixed (consistent with the other MediaError
            // siblings); the underlying probe detail is preserved via `cause` for logs, not
            // echoed to the API caller.
            throw MediaInvalidError("Invalid media", e)
        }

        val existing = mediaRepository.findByPinId(pinId)
        // Promote/save can fail for many reasons: an I/O failure during promote (disk full,
        // permission denied -- FilesystemMediaStore.promote throws java.io.IOException, a
        // checked exception, not a RuntimeException), a DB constraint violation on save, or any
        // other Throwable. Whatever the cause, both the staged temp file AND a
        // promoted-but-unsaved file at storageKey must never be left behind. Catch broadly,
        // clean up both paths, and rethrow unchanged so the caller still sees the original
        // failure.
        // RowMergedOutsideTransaction: an insert of the row `MediaIngestion` just built, which the rule
        // cannot see through the property.
        @Suppress("TooGenericExceptionCaught", "RowMergedOutsideTransaction")
        val saved = try {
            mediaIngestion.promote(ingested)
            mediaRepository.save(ingested.media)
        } catch (e: Exception) {
            // Best-effort: a cleanup failure here must not mask `e`, which is the cause the caller
            // needs to see. The orphan (if any) is reclaimed by the periodic garbage collection.
            mediaIngestion.discard(ingested)
            throw e
        }
        // Deleting the superseded file (and evicting its cached renditions) is best-effort only:
        // the new row is already committed, so a failure here (old file already gone, transient
        // I/O error, ...) must not turn a successful upload into a 500.
        existing?.let { old ->
            mediaStore.deleteQuietly(old.storageKey)
            renditionCache.evictMediaQuietly(old.id)
        }
        clearPinDownload.clear(pinId)
        return SetPinMediaResult(media = saved, replaced = existing != null)
    }

    /** A timeout is the server's failure rather than the file's, so it keeps its own exception. */
    private fun refusalOf(e: VideoProcessorException): Exception =
        when (e) {
            is VideoCodecUnsupportedException -> MediaCodecUnsupportedError(e)
            is VideoTooLongException -> MediaTooLongError(e)
            is UndecodableVideoException -> MediaInvalidError("Invalid video", e)
            is VideoProcessorTimeoutException -> e
        }
}
