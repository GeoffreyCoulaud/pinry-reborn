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
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchedMedia
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageExtractionException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaExtractor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.TooManyRedirectsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.exceptions.PermanentTaskException
import jakarta.enterprise.context.ApplicationScoped
import java.io.FilterInputStream
import java.io.InputStream
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
    private val pageMediaExtractor: PageMediaExtractor,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val renditionCache: RenditionCache,
) {
    fun download(pinId: UUID, context: TaskContext) {
        val downloadRow = mediaDownloadRepository.findByPinId(pinId)
        if (downloadRow == null || downloadRow.status != DownloadStatus.PENDING) return
        val pin = pinRepository.findPinById(pinId) ?: return

        val staged = stageFromSource(pinId, downloadRow.sourceUrl, context)
        val ingested = ingestStaged(pin.author.id, pinId, staged, context)
        promoteAndSwap(pinId, ingested, context)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun stageFromSource(pinId: UUID, sourceUrl: String, context: TaskContext): StagedFile =
        try {
            openSource(sourceUrl, context).use { mediaIngestion.stage(LeaseRenewingStream(it.stream, context)) }
        } catch (e: FetchException) {
            val reason = mapFetch(e)
            if (reason == DownloadReason.UNREACHABLE) {
                failRetryable(pinId, reason, context, e)
            } else {
                failPermanent(pinId, reason)
            }
        } catch (e: PageExtractionException) {
            failPermanent(pinId, mapExtraction(e))
        } catch (ignored: MediaTooLargeException) {
            failPermanent(pinId, DownloadReason.TOO_LARGE)
        } catch (e: Exception) {
            // A failure while streaming the fetched body (connection reset, read timeout, I/O error)
            // is a transient reachability problem. Route it through the failure policy as UNREACHABLE
            // so the download row is updated and an exhausted retry becomes terminal FAILED rather than
            // leaving the row stuck PENDING.
            failRetryable(pinId, DownloadReason.UNREACHABLE, context, e)
        }

    // A page goes to the extractor, anything else down the direct path, where the probe judges (ADR 0048, decision 1).
    private fun openSource(sourceUrl: String, context: TaskContext): FetchedMedia {
        val fetched = mediaFetcher.openStream(sourceUrl)
        if (fetched.contentType?.substringBefore(';')?.trim()?.lowercase() !in PAGE_TYPES) return fetched
        fetched.close()
        return pageMediaExtractor.extract(sourceUrl) { context.renewLeaseIfDue() }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun ingestStaged(ownerId: UUID, pinId: UUID, staged: StagedFile, context: TaskContext): IngestedMedia =
        try {
            mediaIngestion.ingest(staged, ownerId, pinId, clock.now())
        } catch (ignored: MediaTooLargeException) {
            failPermanent(pinId, DownloadReason.TOO_LARGE)
        } catch (e: ImageProbeException) {
            failPermanent(pinId, mapProbe(e))
        } catch (ignored: VideoTooLongException) {
            failPermanent(pinId, DownloadReason.TOO_LONG)
        } catch (ignored: VideoCodecUnsupportedException) {
            failPermanent(pinId, DownloadReason.UNSUPPORTED_CODEC)
        } catch (ignored: UndecodableVideoException) {
            failPermanent(pinId, DownloadReason.INVALID_MEDIA)
        } catch (e: Exception) {
            // A failure outside the declared refusals (a native/FFM error, a processor timeout) is the server's,
            // not the file's: retried, so an exhausted retry ends FAILED rather than leaving the row PENDING.
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

    private fun mapExtraction(e: PageExtractionException): DownloadReason =
        when (e) {
            is PageMediaTooLongException -> DownloadReason.TOO_LONG
            is NoMediaFoundException -> DownloadReason.NO_MEDIA_FOUND
        }

    private fun mapProbe(e: ImageProbeException): DownloadReason =
        when (e) {
            is ImageTooManyPixelsException -> DownloadReason.TOO_MANY_PIXELS
            is UnsupportedImageFormatException -> DownloadReason.UNSUPPORTED_CODEC
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

    private companion object {
        val PAGE_TYPES = setOf("text/html", "application/xhtml+xml")
    }

    /** A body slower than the lease would otherwise be reclaimed mid-fetch and fetched a second time. */
    private class LeaseRenewingStream(source: InputStream, private val context: TaskContext) :
        FilterInputStream(source) {
        override fun read(): Int = super.read().also { context.renewLeaseIfDue() }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { context.renewLeaseIfDue() }
    }
}
