package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import kotlin.math.abs

/**
 * Reads the JSON of `ffprobe -of json -show_streams -show_format -show_data -count_packets` with each packet's
 * `stream_index` and `size`.
 */
internal object FfprobeReport {
    private val mapper = ObjectMapper()
    private const val HALF_TURN = 180
    private const val QUARTER_TURN = 90
    private const val BITS_PER_BYTE = 8
    private const val NANOS_SCALE = 9

    /** [bytes] is the file's size as the store measured it, as an image's probe reports it. */
    fun read(json: String, maxDuration: Duration, bytes: Long): VideoProbeResult {
        val report = mapper.readTree(json)
        val streams = report.path("streams").toList()
        val video = videoTrackOf(streams)
        val videoCodec = videoCodecOf(video)
        val audioTrack = streams.firstOrNull { it.path("codec_type").asText() == "audio" }
        val audio = audioTrack?.let { audioCodecOf(it) to it }
        val duration = durationOf(report.path("format"), maxDuration)
        val rateOf = rates(report.path("packets"), duration)
        val frames = video.path("nb_read_packets").asInt()
        if (frames < 2) throw UndecodableVideoException("The video track holds a single frame")
        val codecs =
            listOfNotNull(
                CodecsParameter.ofVideo(videoCodec, video),
                audio?.let { (codec, track) -> CodecsParameter.ofAudio(codec, track, videoCodec) },
            )
        val (width, height) = displayDimensionsOf(video)
        // The two demuxers the whitelist admits: matroska reads WebM, mov reads MP4.
        val demuxedAs =
            if (report.path("format").path("format_name").asText().startsWith("matroska")) {
                VideoContainer.WEBM
            } else {
                VideoContainer.MP4
            }
        val codecsParameter = codecs.joinToString(",")
        val videoTag = video.path("codec_tag_string").asText()
        val alreadyRepackaged = streams.size == codecs.size && videoTag == codecs.first().substringBefore('.')
        return VideoProbeResult(
            videoCodec,
            audio?.first,
            width,
            height,
            duration,
            frames,
            bytes,
            codecsParameter,
            demuxedAs,
            alreadyRepackaged,
            rateOf(video),
            audioTrack?.let { Media.Sound(it.path("channels").asInt(), rateOf(it)) },
        )
    }

    // A track's packet bits over the file's duration, which repackaging leaves as they are.
    private fun rates(packets: JsonNode, duration: Duration): (JsonNode) -> Long {
        val bytes = packets.groupBy({ it.path("stream_index").asInt() }, { it.path("size").asLong() })
        val seconds = BigDecimal.valueOf(duration.toNanos(), NANOS_SCALE)
        return { track ->
            val bits = bytes[track.path("index").asInt()].orEmpty().sum() * BITS_PER_BYTE
            BigDecimal.valueOf(bits).divide(seconds, RoundingMode.HALF_UP).toLong()
        }
    }

    private fun videoTrackOf(streams: List<JsonNode>): JsonNode =
        streams.firstOrNull { it.path("codec_type").asText() == "video" }
            ?: throw UndecodableVideoException("The file holds no video track")

    private fun videoCodecOf(track: JsonNode): VideoCodec =
        when (val name = track.path("codec_name").asText()) {
            "h264" -> VideoCodec.H264
            "hevc" -> VideoCodec.H265
            "vp9" -> VideoCodec.VP9
            "av1" -> VideoCodec.AV1
            else -> throw VideoCodecUnsupportedException("The video codec $name is not accepted")
        }

    private fun audioCodecOf(track: JsonNode): AudioCodec =
        when (val name = track.path("codec_name").asText()) {
            "aac" -> AudioCodec.AAC
            "opus" -> AudioCodec.OPUS
            "mp3" -> AudioCodec.MP3
            else -> throw VideoCodecUnsupportedException("The audio codec $name is not accepted")
        }

    private fun durationOf(format: JsonNode, maxDuration: Duration): Duration {
        val seconds =
            format.path("duration").asText().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                ?: throw UndecodableVideoException("The file declares no duration")
        val duration = Duration.ofNanos(seconds.movePointRight(9).toLong())
        if (duration > maxDuration) throw VideoTooLongException("The video lasts $duration, past $maxDuration")
        return duration
    }

    private fun displayDimensionsOf(video: JsonNode): Pair<Int, Int> {
        val aspect = video.path("sample_aspect_ratio").asText().split(":").mapNotNull(String::toIntOrNull)
        val (numerator, denominator) = aspect.takeIf { it.size == 2 && it[0] > 0 } ?: listOf(1, 1)
        val width = video.path("width").asInt() * numerator / denominator
        val height = video.path("height").asInt()
        val quarterTurn =
            video.path("side_data_list").any { abs(it.path("rotation").asInt()) % HALF_TURN == QUARTER_TURN }
        return if (quarterTurn) height to width else width to height
    }
}
