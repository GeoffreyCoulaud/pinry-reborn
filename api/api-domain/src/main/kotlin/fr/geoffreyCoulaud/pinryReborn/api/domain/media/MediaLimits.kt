package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import java.time.Duration

/** What a probe measured, its kind being its type: a [ProbeResult] for an image, a [VideoProbeResult] for a video. */
sealed interface MeasuredMedia {
    val width: Int
    val height: Int
    val frames: Int
    val bytes: Long
    val duration: Duration?
}

/** What this instance hosts, read from `media.*`: the one place a pixel bound is compared (ADR 0050, decision 5). */
data class MediaLimits(
    val maxImageBytes: Long,
    val maxVideoBytes: Long,
    val maxVideoDuration: Duration,
    val maxPixelsPerFrame: Long,
) {
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
}
