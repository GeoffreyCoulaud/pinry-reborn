package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import com.fasterxml.jackson.databind.JsonNode
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import java.nio.ByteBuffer
import java.util.HexFormat
import java.util.Locale

/** A track's RFC 6381 `codecs` string, read from the configuration record ffprobe dumps as its extradata. */
@Suppress("MagicNumber") // The bit layouts of the records, as ISO/IEC 14496-15 and the AV1 binding number them.
internal object CodecsParameter {
    private val hex = HexFormat.of().withUpperCase()

    // ffprobe's hexdump: an offset, then eight groups of four hexadecimal digits, then the bytes as text.
    private const val HEXDUMP_WIDTH = 39

    // ffprobe reports no VP9 level (-99); level 1 is what ffmpeg's own codec string and MDN's example write.
    private const val VP9_DEFAULT_LEVEL = 10

    fun ofVideo(codec: VideoCodec, track: JsonNode): String =
        when (codec) {
            VideoCodec.H264 -> "avc1." + hex.formatHex(extradataOf(track, 4), 1, 4)
            VideoCodec.H265 -> hevcOf(extradataOf(track, 13))
            VideoCodec.VP9 -> vp9Of(track)
            VideoCodec.AV1 -> av1Of(extradataOf(track, 3))
        }

    fun ofAudio(codec: AudioCodec, track: JsonNode, videoCodec: VideoCodec): String =
        when (codec) {
            AudioCodec.AAC -> "mp4a.40." + aacObjectTypeOf(extradataOf(track, 2))
            // Opus lands in WebM beside VP9 or AV1 and in MP4 otherwise (decision L1), which spell it apart.
            AudioCodec.OPUS -> if (videoCodec == VideoCodec.VP9 || videoCodec == VideoCodec.AV1) "opus" else "Opus"
            AudioCodec.MP3 -> "mp4a.6B"
        }

    // Tagged hvc1 whatever the source says: the repackaging writes hvc1 (decision L1).
    private fun hevcOf(record: ByteArray): String {
        val general = record[1].toInt()
        val space = listOf("", "A", "B", "C")[general shr 6 and 3]
        val tier = if (general and 0x20 == 0) "L" else "H"
        val compatibility = Integer.toHexString(Integer.reverse(ByteBuffer.wrap(record, 2, 4).int)).uppercase()
        val constraintBytes = record.copyOfRange(6, 12).dropLastWhile { it == 0.toByte() }
        val constraints = constraintBytes.joinToString("") { ".${hex.toHexDigits(it)}" }
        return "hvc1.$space${general and 0x1F}.$compatibility.$tier${record[12].toInt() and 0xFF}$constraints"
    }

    private fun vp9Of(track: JsonNode): String {
        val profile = track.path("profile").asText().filter(Char::isDigit).toIntOrNull() ?: 0
        val depth = Regex("""p(\d+)""").find(track.path("pix_fmt").asText())?.let { it.groupValues[1].toInt() } ?: 8
        return String.format(Locale.ROOT, "vp09.%02d.%02d.%02d", profile, VP9_DEFAULT_LEVEL, depth)
    }

    private fun av1Of(record: ByteArray): String {
        val profile = record[1].toInt() shr 5 and 7
        val level = record[1].toInt() and 0x1F
        val flags = record[2].toInt()
        val tier = if (flags and 0x80 == 0) "M" else "H"
        val depth =
            when {
                flags and 0x40 == 0 -> 8
                flags and 0x20 == 0 -> 10
                else -> 12
            }
        return String.format(Locale.ROOT, "av01.%d.%02d%s.%02d", profile, level, tier, depth)
    }

    private fun aacObjectTypeOf(config: ByteArray): Int {
        val objectType = config[0].toInt() and 0xFF shr 3
        val escaped = (config[0].toInt() and 7 shl 3) or (config[1].toInt() and 0xFF shr 5)
        return if (objectType == 31) 32 + escaped else objectType
    }

    private fun extradataOf(track: JsonNode, minimumSize: Int): ByteArray {
        val digits = track.path("extradata").asText().lines().joinToString("") { line ->
            line.substringAfter(": ", "").take(HEXDUMP_WIDTH).replace(" ", "")
        }
        val bytes = hex.parseHex(digits)
        if (bytes.size < minimumSize) {
            throw UndecodableVideoException("The ${track.path("codec_name").asText()} track carries no configuration")
        }
        return bytes
    }
}
