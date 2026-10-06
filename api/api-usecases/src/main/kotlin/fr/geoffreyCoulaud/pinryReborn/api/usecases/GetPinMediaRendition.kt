package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionMode
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaRenditionUnavailableError
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Semaphore

/** Descriptor of what to serve for a `GET .../media[?size=...]`: the original bytes, or a rendition. */
sealed interface ServedMedia {
    data class Original(val media: Media) : ServedMedia

    data class Rendition(val mediaId: UUID, val key: String) : ServedMedia
}

@ApplicationScoped
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class GetPinMediaRendition(
    private val getPinMedia: GetPinMedia,
    private val mediaStore: MediaStore,
    private val imageTransformer: ImageTransformer,
    private val renditionCache: RenditionCache,
    private val videoProcessor: VideoProcessor,
    private val limits: MediaLimits,
    private val clock: Clock,
) {
    private val renders = Semaphore(limits.renderConcurrency, true)

    fun get(pinId: UUID, requester: User, requestedPx: Int?, animated: Boolean?): ServedMedia {
        // Reuse 2a's load + owner/not-found guards verbatim (403/404 behaviour unchanged).
        val media = getPinMedia.get(pinId, requester)
        // Left unsaid, an image keeps its animation and a video gives its poster (decision D1).
        val requestedAnimated = animated ?: (media !is Media.Video)
        // The requested flag is a no-op on a non-animated source (spec section 3), so intersect it
        // with the source before it reaches the key, the spec, or the descriptor. Without this a
        // static original renders under an "-a" key: identical bytes cached twice and served under
        // two ETags, and the transformer is told to decode frames from a source that has none.
        val effectiveAnimated = requestedAnimated && media.animated
        val effectivePx = effectiveRenditionPx(media, requestedPx, requestedAnimated)
        return if (effectivePx == null) {
            ServedMedia.Original(media)
        } else {
            serveRendition(media, effectivePx, effectiveAnimated)
        }
    }

    // The clamped shortest-side px for a rendition, or null when the original must be served as-is
    // (no size requested, or an image that needs neither downscaling nor flattening). A video's
    // original is never served to an `<img>`.
    private fun effectiveRenditionPx(media: Media, requestedPx: Int?, animated: Boolean): Int? {
        if (requestedPx == null) return null
        val srcShort = minOf(media.width, media.height)
        val needsDownscale = srcShort > requestedPx
        val needsFlatten = media.animated && !animated
        return if (media is Media.Video || needsDownscale || needsFlatten) minOf(requestedPx, srcShort) else null
    }

    // What is rendered is what MediaLimits judges, so the key names that rather than what was asked (ADR 0050).
    private fun serveRendition(media: Media, effectivePx: Int, animated: Boolean): ServedMedia.Rendition {
        val mode = limits.renditionOf(media, effectivePx, animated)
        if (mode == RenditionMode.NONE) throw MediaRenditionUnavailableError()
        val rendersAnimated = animated && mode == RenditionMode.WHOLE
        val fromOneFrame = mode == RenditionMode.ONE_FRAME_POSTER
        val key = keyFor(effectivePx, rendersAnimated, fromOneFrame)
        if (!isCached(media.id, key)) {
            renderMiss(media.id, key) {
                if (media is Media.Video) {
                    renderVideo(media, effectivePx, rendersAnimated, fromOneFrame)
                } else {
                    mediaStore.openStream(media.storageKey).use { source ->
                        val spec = RenditionSpec(effectivePx, rendersAnimated, media.width, media.height)
                        imageTransformer.render(source, spec)
                    }
                }
            }
        }
        return ServedMedia.Rendition(media.id, key)
    }

    // Every miss waits its turn, without a time limit; the rendition or its failure may land meanwhile (ADR 0050).
    private fun renderMiss(mediaId: UUID, key: String, render: () -> StagedFile) {
        val failure = "$key.failed-${limits.decoderTimeout.seconds}-${limits.decoderMemory}"
        if (failedRecently(mediaId, failure)) throw MediaRenditionUnavailableError()
        renders.acquire()
        try {
            if (isCached(mediaId, key)) return
            if (failedRecently(mediaId, failure)) throw MediaRenditionUnavailableError()
            renditionCache.store(mediaId, key, decode(mediaId, failure, render))
        } finally {
            renders.release()
        }
    }

    // A decoder's refusal or timeout is marked, not replayed for a day; a process that cannot start marks nothing.
    private fun decode(mediaId: UUID, failure: String, render: () -> StagedFile): StagedFile =
        try {
            render()
        } catch (refused: UndecodableImageException) {
            throw markFailed(mediaId, failure, refused)
        } catch (refused: VideoProcessorException) {
            throw markFailed(mediaId, failure, refused)
        }

    private fun markFailed(mediaId: UUID, failure: String, cause: Exception): MediaRenditionUnavailableError {
        logger.warn(cause) { "media $mediaId: rendition unavailable, marked $failure" }
        renditionCache.mark(mediaId, failure)
        return MediaRenditionUnavailableError(cause)
    }

    // The host may be the cause (an out-of-memory kill, a full disk, a loaded machine), so a marker expires.
    private fun failedRecently(mediaId: UUID, failure: String): Boolean {
        val markedAt = renditionCache.markedAt(mediaId, failure) ?: return false
        return !markedAt.isBefore(clock.now() - FAILURE_LIFETIME)
    }

    private fun isCached(mediaId: UUID, key: String): Boolean =
        renditionCache.openStream(mediaId, key)?.use { true } ?: false

    // The processor reads a file, so the original is staged for the time of one rendition.
    private fun renderVideo(media: Media, effectivePx: Int, animated: Boolean, fromOneFrame: Boolean): StagedFile {
        val original = mediaStore.stageStored(media)
        try {
            return if (animated) {
                videoProcessor.preview(original, effectivePx)
            } else {
                drawPoster(media, original, effectivePx, fromOneFrame)
            }
        } finally {
            mediaStore.discardQuietly(original)
        }
    }

    // The poster keeps the video's orientation, which is all the transformer reads of the frame's dimensions.
    private fun drawPoster(media: Media, original: StagedFile, effectivePx: Int, fromOneFrame: Boolean): StagedFile {
        val poster = videoProcessor.poster(original, effectivePx, fromOneFrame)
        val spec = RenditionSpec(effectivePx, false, media.width, media.height)
        try {
            return mediaStore.openStaged(poster).use { imageTransformer.render(it, spec) }
        } finally {
            mediaStore.discardQuietly(poster)
        }
    }

    // The controller's ETag is built from the key, so one key names one set of bytes and one validator.
    private fun keyFor(effectivePx: Int, animated: Boolean, fromOneFrame: Boolean): String {
        val frames = if (animated) "a" else if (fromOneFrame) "s1" else "s"
        return "$ENCODER_VERSION-$effectivePx-$frames.webp"
    }

    private companion object {
        /** Bumped whenever the rendition encoding changes, which orphans every cached file and its ETag. */
        const val ENCODER_VERSION = "v2"

        /** Chosen, not measured: a failure is replayed at most once a day (specification decision D2). */
        val FAILURE_LIFETIME: Duration = Duration.ofHours(24)

        val logger = KotlinLogging.logger {}
    }
}
