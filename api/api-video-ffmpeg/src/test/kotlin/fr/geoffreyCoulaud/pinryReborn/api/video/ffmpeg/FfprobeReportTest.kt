package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

/** The cases no fixture reaches, written as ffprobe's JSON. */
class FfprobeReportTest {
    private val mapper = ObjectMapper()
    private val maxDuration = Duration.ofSeconds(120)

    // ffprobe's hexdump of one line of at most sixteen bytes.
    private fun dump(hex: String) = "\n00000000: " + hex.chunked(4).joinToString(" ").padEnd(39) + "  ascii\n"

    private fun track(type: String, codec: String, vararg fields: Pair<String, Any>) =
        mapOf("codec_type" to type, "codec_name" to codec, "width" to 160, "height" to 120) +
            ("nb_read_packets" to "10") + fields

    private fun h264(vararg fields: Pair<String, Any>) =
        track("video", "h264", "extradata" to dump("0164000a"), *fields)

    private fun report(vararg tracks: Map<String, Any>) =
        mapper.writeValueAsString(mapOf("streams" to tracks.toList(), "format" to mapOf("duration" to "1.000000")))

    private fun codecsOf(vararg tracks: Map<String, Any>) = FfprobeReport.read(report(*tracks), maxDuration).codecs

    private fun lasting(format: Map<String, String>) =
        mapper.writeValueAsString(mapOf("streams" to listOf(h264()), "format" to format))

    @Test
    fun `Given an unlisted audio codec, Then read refuses it naming the codec`() {
        val exception =
            assertThrows(VideoCodecUnsupportedException::class.java) {
                codecsOf(h264(), track("audio", "ac3"))
            }
        assertEquals("The audio codec ac3 is not accepted", exception.message)
    }

    @Test
    fun `Given no duration, Then read refuses the file`() {
        assertThrows(UndecodableVideoException::class.java) {
            FfprobeReport.read(lasting(emptyMap()), maxDuration)
        }
    }

    @Test
    fun `Given a duration past the bound, Then read refuses it as too long`() {
        assertThrows(VideoTooLongException::class.java) {
            FfprobeReport.read(lasting(mapOf("duration" to "121.000000")), maxDuration)
        }
    }

    private fun dimensionsTurned(degrees: Int): Pair<Int, Int> {
        val turn = mapOf("side_data_type" to "Display Matrix", "rotation" to degrees)
        val result = FfprobeReport.read(report(h264("side_data_list" to listOf(turn))), maxDuration)
        return result.width to result.height
    }

    @Test
    fun `Given a quarter turn either way, Then the width and height swap, and a half turn keeps them`() {
        assertEquals(120 to 160, dimensionsTurned(-90))
        assertEquals(160 to 120, dimensionsTurned(180))
    }

    @Test
    fun `Given Opus beside VP9 or AV1, Then its parameter is spelled as WebM does`() {
        val vp9 = track("video", "vp9", "pix_fmt" to "yuv420p")
        val av1 = track("video", "av1", "extradata" to dump("81000c"))
        assertEquals("vp09.00.10.08,opus", codecsOf(vp9, track("audio", "opus")))
        assertEquals("av01.0.00M.08,opus", codecsOf(av1, track("audio", "opus")))
    }

    @Test
    fun `Given no video track, Then read refuses the file`() {
        assertThrows(UndecodableVideoException::class.java) {
            FfprobeReport.read(report(track("audio", "aac")), maxDuration)
        }
    }

    @Test
    fun `Given an unlisted video codec, Then read refuses it naming the codec`() {
        val exception =
            assertThrows(VideoCodecUnsupportedException::class.java) {
                FfprobeReport.read(report(track("video", "mpeg4")), maxDuration)
            }
        assertEquals("The video codec mpeg4 is not accepted", exception.message)
    }

    @Test
    fun `Given a video track of one frame, Then read refuses it`() {
        assertThrows(UndecodableVideoException::class.java) {
            FfprobeReport.read(report(h264("nb_read_packets" to "1")), maxDuration)
        }
    }

    @Test
    fun `Given no codec configuration, Then read refuses the track`() {
        assertThrows(UndecodableVideoException::class.java) {
            FfprobeReport.read(report(h264("extradata" to "\n")), maxDuration)
        }
    }

    @Test
    fun `Given a pixel aspect ratio of 2 to 1, Then the width displayed is doubled, and square otherwise`() {
        val width = { fields: Array<Pair<String, Any>> -> FfprobeReport.read(report(h264(*fields)), maxDuration).width }
        assertEquals(320, width(arrayOf("sample_aspect_ratio" to "2:1")))
        assertEquals(160, width(arrayOf("sample_aspect_ratio" to "0:1")))
        assertEquals(160, width(emptyArray()))
    }

    @Test
    fun `Given H265 records, Then its parameter carries the space, the tier and the constraints but trailing zeros`() {
        val hevc = { record: String -> track("video", "hevc", "extradata" to dump(record)) }
        assertEquals("hvc1.A1.6.H93", codecsOf(hevc("01" + "61" + "60000000" + "000000000000" + "5d")))
        assertEquals("hvc1.1.6.L30.90", codecsOf(hevc("01" + "01" + "60000000" + "900000000000" + "1e")))
    }

    @Test
    fun `Given AV1 at 10 and 12 bits, Then its parameter carries the tier and the depth`() {
        assertEquals("av01.1.08H.10", codecsOf(track("video", "av1", "extradata" to dump("8128c0"))))
        assertEquals("av01.2.08M.12", codecsOf(track("video", "av1", "extradata" to dump("814860"))))
    }

    @Test
    fun `Given VP9 at 10 bits or with no profile, Then its parameter carries the depth or profile 0`() {
        val tenBits = track("video", "vp9", "profile" to "Profile 2", "pix_fmt" to "yuv420p10le")
        assertEquals("vp09.02.10.10", codecsOf(tenBits))
        assertEquals("vp09.00.10.08", codecsOf(track("video", "vp9", "pix_fmt" to "yuv420p")))
    }

    @Test
    fun `Given Opus or MP3 beside H264, Then their parameters are spelled as MP4 does`() {
        assertEquals("avc1.64000A,Opus", codecsOf(h264(), track("audio", "opus")))
        assertEquals("avc1.64000A,mp4a.6B", codecsOf(h264(), track("audio", "mp3")))
    }

    @Test
    fun `Given AAC with an escaped object type, Then its parameter carries the escaped value`() {
        // Object type 31 escapes to 32 plus the next six bits: 42, USAC.
        assertEquals("avc1.64000A,mp4a.40.42", codecsOf(h264(), track("audio", "aac", "extradata" to dump("f940"))))
    }
}
