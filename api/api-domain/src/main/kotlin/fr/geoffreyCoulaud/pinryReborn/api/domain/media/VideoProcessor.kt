package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.time.Duration

enum class VideoCodec { H264, H265, VP9, AV1 }

enum class AudioCodec { AAC, OPUS, MP3 }

/**
 * A video's first video track and first audio track, if any. [width] and [height] are what it displays at, and
 * [codecs] is the RFC 6381 `codecs` parameter of the two tracks (ADR 0047, decision 6), [demuxedAs] its container now.
 */
data class VideoProbeResult(
    val videoCodec: VideoCodec,
    val audioCodec: AudioCodec?,
    val width: Int,
    val height: Int,
    val duration: Duration,
    val codecs: String,
    val demuxedAs: VideoContainer,
)

interface VideoProcessor {
    /**
     * Read the staged file's tracks. Throws a [VideoProcessorException] on an unlisted codec, a duration past
     * [maxDuration], a missing duration, a single frame, or a file its two demuxers refuse.
     */
    fun probe(staged: StagedFile, maxDuration: Duration): VideoProbeResult

    // Each method below writes a fresh file beside [staged], owned by the caller, and refuses what the demuxers refuse.

    /** The first video and first audio tracks, never re-encoded, in the [VideoContainer] their codecs choose. */
    fun repackage(staged: StagedFile, video: VideoProbeResult): StagedFile

    /** The frame that best represents the start of the video, as a PNG at the dimensions it displays at. */
    fun poster(staged: StagedFile): StagedFile

    /** The first three seconds as an animated WebP whose shortest side is [shortestSide]. */
    fun preview(staged: StagedFile, shortestSide: Int): StagedFile
}
