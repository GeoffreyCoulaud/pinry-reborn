package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.time.Duration

/** One frame's luminance, row by row, from 0 to 255. */
class LumaFrame(val width: Int, val height: Int, val luma: FloatArray) {
    companion object {
        /** [rgb] holds three samples a pixel, weighted as PDQ weighs them (Meta's `pdq/cpp`, `downscaling`). */
        fun ofRgb(width: Int, height: Int, rgb: IntArray) =
            LumaFrame(
                width,
                height,
                FloatArray(width * height) { pixel ->
                    val red = rgb[pixel * 3]
                    val green = rgb[pixel * 3 + 1]
                    val blue = rgb[pixel * 3 + 2]
                    LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue
                },
            )

        private const val LUMA_RED = 0.299f
        private const val LUMA_GREEN = 0.587f
        private const val LUMA_BLUE = 0.114f
    }
}

/** A media's frames, sampled at the instants of ADR 0051 and handed over one at a time. */
interface FrameSampler {
    /**
     * Hands [onFrame] each sampled frame of [media], staged as [staged], at most [MediaLimits.MAX_FRAME_SIDE] on its
     * longer side. Throws what its decoder refuses, and an `IOException` on a frame it cannot read back.
     */
    fun sample(media: Media, staged: StagedFile, onFrame: (LumaFrame) -> Unit)

    companion object {
        private const val MIN_FRAMES = 4

        // The longest video's seconds (ADR 0047), so an animated image of any length costs no more decoder runs.
        private const val MAX_FRAMES = 120L
        private val SECOND: Duration = Duration.ofSeconds(1)

        /** Each whole second before [length], 120 at most; under four seconds, evenly spaced ones up to four. */
        fun instants(length: Duration): List<Duration> {
            val seconds = Math.ceilDiv(length.toMillis(), SECOND.toMillis()).coerceAtMost(MAX_FRAMES)
            val whole = (0 until seconds).map { SECOND.multipliedBy(it) }
            val evenlySpaced = (0L until MIN_FRAMES).map { length.multipliedBy(it).dividedBy(MIN_FRAMES.toLong()) }
            val added = (evenlySpaced - whole.toSet()).distinct().take((MIN_FRAMES - whole.size).coerceAtLeast(0))
            return (whole + added).sorted()
        }

        /** The pages of an animated image shown at [instants] of its timeline, or spread over it when it has none. */
        fun pages(delays: List<Duration>): List<Int> {
            val starts = delays.runningFold(Duration.ZERO, Duration::plus)
            val length = starts.last()
            if (length.isZero) return (0 until MIN_FRAMES).map { it * delays.size / MIN_FRAMES }.distinct()
            return instants(length).map { instant -> starts.indexOfLast { it <= instant } }.distinct()
        }
    }
}
