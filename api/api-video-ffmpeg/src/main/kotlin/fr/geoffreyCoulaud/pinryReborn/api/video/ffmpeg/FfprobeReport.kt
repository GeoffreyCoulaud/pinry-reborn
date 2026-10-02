package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import java.time.Duration
import kotlin.math.abs

/** Reads the JSON of `ffprobe -of json -show_streams -show_format -show_data -count_packets`. */
internal object FfprobeReport {
    private val mapper = ObjectMapper()
    private const val HALF_TURN = 180
    private const val QUARTER_TURN = 90

    fun read(json: String, maxDuration: Duration): VideoProbeResult {
        val report = mapper.readTree(json)
        val streams = report.path("streams").toList()
        val video = videoTrackOf(streams)
        val videoCodec = videoCodecOf(video)
        val audio = streams.firstOrNull { it.path("codec_type").asText() == "audio" }?.let { audioCodecOf(it) to it }
        val duration = durationOf(report.path("format"), maxDuration)
        if (video.path("nb_read_packets").asLong() < 2) {
            throw UndecodableVideoException("The video track holds a single frame")
        }
        val codecs =
            listOfNotNull(
                CodecsParameter.ofVideo(videoCodec, video),
                audio?.let { (codec, track) -> CodecsParameter.ofAudio(codec, track, videoCodec) },
            )
        val (width, height) = displayDimensionsOf(video)
        return VideoProbeResult(videoCodec, audio?.first, width, height, duration, codecs.joinToString(","))
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
            format.path("duration").asText().toBigDecimalOrNull()
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
