package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import java.time.Duration

/** What a probe measured, its kind being its type: a [ProbeResult] for an image, a [VideoProbeResult] for a video. */
sealed interface MeasuredMedia {
    val width: Int
    val height: Int
    val frames: Int
    val bytes: Long
    val duration: Duration?
}

/** What a rendition draws, richest first (ADR 0050, decision 2). */
enum class RenditionMode {
    WHOLE,

    /** The static rendition in place of the animated one: an image's first frame, a video's poster. */
    FIRST_FRAME,

    /** A video's poster drawn from its first frame rather than chosen among a hundred. */
    ONE_FRAME_POSTER,

    /** A frame past the per-frame bound, stored before the bound was lowered. */
    NONE,
}

/** What this instance hosts, read from `media.*`: the one place a pixel bound is compared (ADR 0050, decision 5). */
data class MediaLimits(
    val maxImageBytes: Long,
    val maxVideoBytes: Long,
    val maxVideoDuration: Duration,
    val maxPixelsPerFrame: Long,
    val maxPixelsPerRender: Long,
    val renderConcurrency: Int,
    val decoderTimeout: Duration,
    val decoderMemory: Long,
) {
    /** What a rendition [px] on its shortest side draws of [media], [animated] being already intersected with it. */
    fun renditionOf(media: Media, px: Int, animated: Boolean): RenditionMode {
        val frame = media.width.toLong() * media.height
        val static = if (animated) RenditionMode.FIRST_FRAME else RenditionMode.WHOLE
        return when {
            frame > maxPixelsPerFrame -> RenditionMode.NONE
            animated && frame * animatedFrames(media) <= maxPixelsPerRender -> RenditionMode.WHOLE
            media !is Media.Video || posterFits(media, px, frame) -> static
            else -> RenditionMode.ONE_FRAME_POSTER
        }
    }

    // An animated image decodes every frame, a video's preview its first seconds.
    private fun animatedFrames(media: Media): Long =
        if (media is Media.Video && media.duration > PREVIEW) {
            Math.ceilDiv(media.frames * PREVIEW.toMillis(), media.duration.toMillis())
        } else {
            media.frames.toLong()
        }

    // The poster's thumbnail holds its frames at the output's size and decodes as many of the source's.
    private fun posterFits(media: Media, px: Int, frame: Long): Boolean {
        val output = px.toLong() * px * maxOf(media.width, media.height) / minOf(media.width, media.height)
        return output * POSTER_FRAMES <= maxPixelsPerFrame &&
            frame * minOf(media.frames, POSTER_FRAMES) <= maxPixelsPerRender
    }

    /** Staging precedes the probe, so it admits the larger bound and [refuseIfOver] applies each kind's own. */
    val maxStagedBytes: Long get() = maxOf(maxImageBytes, maxVideoBytes)

    fun refuseIfOver(measured: MeasuredMedia) {
        // The image's refusal for a video too, so each caller answers a video's pixels as it answers an image's.
        if (measured.width.toLong() * measured.height > maxPixelsPerFrame) {
            throw ImageTooManyPixelsException(
                "${measured.width}x${measured.height}, past $maxPixelsPerFrame pixels per frame",
            )
        }
        val maxBytes =
            when (measured) {
                is ProbeResult -> maxImageBytes
                is VideoProbeResult -> maxVideoBytes
            }
        if (measured.bytes > maxBytes) throw MediaTooLargeException("${measured.bytes} bytes, past $maxBytes")
    }

    companion object {
        /** The longer side a sampled frame is decoded at, at most (ADR 0051). */
        const val MAX_FRAME_SIDE = 512

        // ffmpeg's `-t 3` for a preview and `thumbnail=n=100` for a poster.
        private val PREVIEW: Duration = Duration.ofSeconds(3)
        private const val POSTER_FRAMES = 100
    }
}
