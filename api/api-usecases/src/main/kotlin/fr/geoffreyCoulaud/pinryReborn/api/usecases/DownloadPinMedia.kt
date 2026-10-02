package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchAccessDeniedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchFailedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchNotFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTooManyPixelsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.TooManyRedirectsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.exceptions.PermanentTaskException
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class DownloadPinMedia(
    private val pinRepository: PinRepositoryInterface,
    private val mediaRepository: MediaRepositoryInterface,
    private val mediaDownloadRepository: MediaDownloadRepositoryInterface,
    private val mediaStore: MediaStore,
    private val mediaIngestion: MediaIngestion,
    private val mediaFetcher: MediaFetcher,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val renditionCache: RenditionCache,
) {
    fun download(pinId: UUID, context: TaskContext, maxBytes: Long, maxPixels: Long) {
        val downloadRow = mediaDownloadRepository.findByPinId(pinId)
        if (downloadRow == null || downloadRow.status != DownloadStatus.PENDING) return
        val pin = pinRepository.findPinById(pinId) ?: return

        val staged = stageFromSource(pinId, downloadRow.sourceUrl, maxBytes, context)
        val ingested = ingestStaged(pin.author.id, pinId, staged, maxPixels, context)
        promoteAndSwap(pinId, ingested, context)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun stageFromSource(pinId: UUID, sourceUrl: String, maxBytes: Long, context: TaskContext): StagedFile =
        try {
            mediaFetcher.openStream(sourceUrl).use { mediaIngestion.stage(it, maxBytes) }
        } catch (e: FetchException) {
            val reason = mapFetch(e)
            if (reason == DownloadReason.UNREACHABLE) {
                failRetryable(pinId, reason, context, e)
            } else {
                failPermanent(pinId, reason)
            }
        } catch (ignored: MediaTooLargeException) {
            failPermanent(pinId, DownloadReason.TOO_LARGE)
        } catch (e: Exception) {
            // A failure while streaming the fetched body (connection reset, read timeout, I/O error)
            // is a transient reachability problem. Route it through the failure policy as UNREACHABLE
            // so the download row is updated and an exhausted retry becomes terminal FAILED rather than
            // leaving the row stuck PENDING.
            failRetryable(pinId, DownloadReason.UNREACHABLE, context, e)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun ingestStaged(
        ownerId: UUID,
        pinId: UUID,
        staged: StagedFile,
        maxPixels: Long,
        context: TaskContext,
    ): IngestedMedia =
        try {
            mediaIngestion.ingest(staged, ownerId, pinId, maxPixels, clock.now())
        } catch (e: ImageProbeException) {
            failPermanent(pinId, mapProbe(e))
        } catch (e: Exception) {
            // A probe failure outside the declared ImageProbeException contract (e.g. a native/FFM
            // error) must still route through the failure policy; otherwise the download row is
            // left stuck PENDING. Treat it as a transient internal error so an exhausted retry
            // becomes terminal FAILED instead of DEAD-with-PENDING-row.
            failRetryable(pinId, DownloadReason.INTERNAL_ERROR, context, e)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun promoteAndSwap(pinId: UUID, ingested: IngestedMedia, context: TaskContext) {
        val media = ingested.media
        val superseded = mediaRepository.findByPinId(pinId)
        try {
            mediaIngestion.promote(ingested)
            val swapped =
                transactionRunner.inTransaction {
                    if (mediaDownloadRepository.deleteIfPending(pinId) > 0) {
                        mediaRepository.save(media)
                        true
                    } else {
                        false
                    }
                }
            if (swapped) {
                // Best-effort delete of the superseded file (and eviction of its cached
                // renditions) on a successful mode-B replace (spec section 8 step 7), mirroring
                // the mode-A path: the new row is committed, so a failure here must not fail the
                // task. Only after a real swap; a no-op swap kept the old image, which must not
                // be touched.
                superseded?.let { old ->
                    mediaStore.deleteQuietly(old.storageKey)
                    renditionCache.evictMediaQuietly(old.id)
                }
            } else {
                // A no-op swap is itself a success; deleting the freshly promoted file is
                // best-effort cleanup. A failure here must not turn a success into a retry.
                mediaStore.deleteQuietly(media.storageKey)
            }
        } catch (e: Exception) {
            // Best-effort: a cleanup failure must not mask `e`, which the retry policy records
            // and rethrows. The orphan (if any) is reclaimed by the periodic garbage collection.
            mediaIngestion.discard(ingested)
            failRetryable(pinId, DownloadReason.INTERNAL_ERROR, context, e)
        }
    }

    private fun mapFetch(e: FetchException): DownloadReason =
        when (e) {
            is UrlNotAllowedException -> DownloadReason.URL_NOT_ALLOWED
            is FetchAccessDeniedException -> DownloadReason.ACCESS_DENIED
            is FetchNotFoundException -> DownloadReason.NOT_FOUND
            is FetchTooLargeException -> DownloadReason.TOO_LARGE
            is TooManyRedirectsException -> DownloadReason.FETCH_FAILED
            is FetchFailedException -> DownloadReason.FETCH_FAILED
            is FetchUnreachableException -> DownloadReason.UNREACHABLE
        }

    private fun mapProbe(e: ImageProbeException): DownloadReason =
        when (e) {
            is ImageTooManyPixelsException -> DownloadReason.TOO_MANY_PIXELS
            is UnsupportedImageFormatException -> DownloadReason.INVALID_MEDIA
            is UndecodableImageException -> DownloadReason.INVALID_MEDIA
        }

    private fun failPermanent(pinId: UUID, reason: DownloadReason): Nothing {
        mediaDownloadRepository.markFailed(pinId, reason, clock.now())
        throw PermanentTaskException(reason.name)
    }

    private fun failRetryable(pinId: UUID, reason: DownloadReason, context: TaskContext, cause: Exception): Nothing {
        if (context.attempt >= context.maxAttempts) {
            mediaDownloadRepository.markFailed(pinId, reason, clock.now())
            throw PermanentTaskException(reason.name)
        }
        mediaDownloadRepository.recordLastError(pinId, cause.message ?: reason.name, clock.now())
        throw cause
    }
}
