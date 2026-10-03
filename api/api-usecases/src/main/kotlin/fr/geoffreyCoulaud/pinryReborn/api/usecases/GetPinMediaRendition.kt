package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** Descriptor of what to serve for a `GET .../media[?size=...]`: the original bytes, or a rendition. */
sealed interface ServedMedia {
    data class Original(val media: Media) : ServedMedia
    data class Rendition(val mediaId: UUID, val key: String, val effectivePx: Int, val animated: Boolean) : ServedMedia
}

@ApplicationScoped
class GetPinMediaRendition(
    private val getPinMedia: GetPinMedia,
    private val mediaStore: MediaStore,
    private val imageTransformer: ImageTransformer,
    private val renditionCache: RenditionCache,
    private val videoProcessor: VideoProcessor,
) {
    fun get(pinId: UUID, requester: User, requestedPx: Int?, animated: Boolean?): ServedMedia {
        // Reuse 2a's load + owner/not-found guards verbatim (403/404 behaviour unchanged).
        val media = getPinMedia.get(pinId, requester)
        // Left unsaid, an image keeps its animation and a video gives its poster (decision D1).
        val requestedAnimated = animated ?: !media.isVideo
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
        return if (media.isVideo || needsDownscale || needsFlatten) minOf(requestedPx, srcShort) else null
    }

    private fun serveRendition(media: Media, effectivePx: Int, animated: Boolean): ServedMedia.Rendition {
        val key = keyFor(effectivePx, animated)
        val cached = renditionCache.openStream(media.id, key)
        if (cached != null) {
            cached.close()
            return ServedMedia.Rendition(media.id, key, effectivePx, animated)
        }
        val staged =
            if (media.isVideo) {
                renderVideo(media, effectivePx, animated)
            } else {
                mediaStore.openStream(media.storageKey).use { source ->
                    imageTransformer.render(source, RenditionSpec(effectivePx, animated))
                }
            }
        renditionCache.store(media.id, key, staged)
        return ServedMedia.Rendition(media.id, key, effectivePx, animated)
    }

    // The processor reads a file, so the original is staged for the time of one rendition.
    private fun renderVideo(media: Media, effectivePx: Int, animated: Boolean): StagedFile {
        val original = mediaStore.openStream(media.storageKey).use { mediaStore.stage(it, media.byteSize) }
        try {
            return if (animated) videoProcessor.preview(original, effectivePx) else drawPoster(original, effectivePx)
        } finally {
            mediaStore.discardQuietly(original)
        }
    }

    private fun drawPoster(original: StagedFile, effectivePx: Int): StagedFile {
        val poster = videoProcessor.poster(original)
        try {
            return mediaStore.openStaged(poster).use { imageTransformer.render(it, RenditionSpec(effectivePx, false)) }
        } finally {
            mediaStore.discardQuietly(poster)
        }
    }

    private val Media.isVideo: Boolean get() = mimeType.startsWith("video/")

    private fun keyFor(effectivePx: Int, animated: Boolean): String =
        "$ENCODER_VERSION-$effectivePx-${if (animated) "a" else "s"}.webp"

    companion object {
        /**
         * Bumped whenever the rendition encoding changes, to invalidate previously generated
         * renditions cleanly (spec section 9).
         *
         * It is deliberately part of BOTH the cache key (here) and the ETag the controller derives
         * from this same constant, so one bump orphans every cached file AND mints fresh
         * validators. Versioning only the ETag would be worse than not versioning it at all: the
         * client would refetch, hit the old bytes under the unchanged key, and get them stamped
         * with the new ETag, pinning the staleness permanently.
         */
        const val ENCODER_VERSION = "v1"
    }
}
