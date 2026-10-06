package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.AudioCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodecUnsupportedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoTooLongException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FfmpegVideoProcessorTest {
    private val processor = FfmpegVideoProcessor(Duration.ofSeconds(60), DECODER_MEMORY, webpQuality = 75)
    private val maxDuration = Duration.ofSeconds(120)

    @TempDir lateinit var directory: Path

    private fun fixture(name: String) = Path.of("src/test/resources/fixtures", name)

    private fun staged(name: String) = StagedFile(path = fixture(name).toString(), byteSize = 0, contentHash = "")

    // A copy in the test's own directory, where the processor writes its output beside it.
    private fun copied(name: String): StagedFile {
        val copy = Files.copy(fixture(name), directory.resolve(name))
        return StagedFile(path = copy.toString(), byteSize = 0, contentHash = "")
    }

    private fun repackaged(name: String): StagedFile {
        val source = copied(name)
        return processor.repackage(source, processor.probe(source, maxDuration))
    }

    // What ADR 0047 decision 1 compares: the kept tracks' codec, profile, level and packet count.
    private fun tracksOf(staged: StagedFile): List<Map<String, String>> {
        val entries = "stream=codec_type,codec_name,profile,level,nb_read_packets,codec_tag_string"
        val command = listOf("ffprobe", "-v", "error", "-count_packets", "-show_entries", entries, "-of", "json")
        val json = ProcessBuilder(command + staged.path).start().inputStream.readAllBytes().decodeToString()
        return ObjectMapper()
            .readTree(json)
            .path("streams")
            .filter { it.path("codec_type").asText() in setOf("video", "audio") }
            .map { track -> track.properties().associate { (key, value) -> key to value.asText() } }
    }

    private fun containerOf(staged: StagedFile): String {
        val header = String(Files.readAllBytes(Path.of(staged.path)).copyOf(48), Charsets.ISO_8859_1)
        return when {
            header.substring(4, 8) == "ftyp" -> "mp4"
            header.contains("webm") -> "webm"
            else -> "unknown"
        }
    }

    private fun assertRepackagedInto(container: String, name: String) {
        val output = repackaged(name)
        assertEquals(container, containerOf(output), name)
        val compared = setOf("codec_type", "codec_name", "profile", "level", "nb_read_packets")
        fun keptOf(file: StagedFile) = tracksOf(file).map { track -> track.filterKeys { it in compared } }
        assertEquals(keptOf(staged(name)), keptOf(output), name)
    }

    // The canvas and each frame's duration, read from the RIFF chunks of an animated WebP.
    private fun animationOf(staged: StagedFile): Pair<Pair<Int, Int>, List<Int>> {
        val bytes = Files.readAllBytes(Path.of(staged.path))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun uint24(at: Int) = buffer.getInt(at) and 0xFFFFFF
        var canvas = 0 to 0
        val durations = mutableListOf<Int>()
        var offset = 12
        while (offset < bytes.size) {
            val payload = offset + 8
            when (String(bytes, offset, 4, Charsets.US_ASCII)) {
                "VP8X" -> canvas = uint24(payload + 4) + 1 to uint24(payload + 7) + 1
                "ANMF" -> durations += uint24(payload + 12)
            }
            val size = buffer.getInt(offset + 4)
            offset = payload + size + size % 2
        }
        return canvas to durations
    }

    @Test
    fun `Given H264 or H265, Then repackage writes MP4 with the same tracks`() {
        assertRepackagedInto("mp4", "h264-aac.mkv")
        assertRepackagedInto("mp4", "h265-hev1-aac.mov")
    }

    @Test
    fun `Given H265 tagged hev1, Then repackage tags it hvc1`() {
        val output = repackaged("h265-hev1-aac.mov")
        assertEquals("hvc1", tracksOf(output).first().getValue("codec_tag_string"))
    }

    @Test
    fun `Given VP9 or AV1, Then repackage writes WebM with the same tracks`() {
        assertRepackagedInto("webm", "vp9-opus.webm")
        assertRepackagedInto("webm", "av1.mp4")
    }

    @Test
    fun `Given the same fixture repackaged twice, Then both outputs hold the same bytes`() {
        for (name in listOf("h264-aac.mkv", "vp9-opus.webm")) {
            // Given
            val source = copied(name)
            val video = processor.probe(source, maxDuration)
            // When
            val first = processor.repackage(source, video)
            val second = processor.repackage(source, video)
            // Then
            assertEquals(first.contentHash, second.contentHash, name)
            assertTrue(Files.readAllBytes(Path.of(first.path)).contentEquals(Files.readAllBytes(Path.of(second.path))))
        }
    }

    @Test
    fun `Given a stored WebM repackaged again, Then it keeps its bytes, which lets an import repackage it`() {
        for (name in listOf("av1.mp4", "vp9-opus.webm")) {
            // Given: what an upload stores, AV1 from an MP4 carrying brand tags among them
            val source = copied(name)
            val stored = processor.repackage(source, processor.probe(source, maxDuration))
            // When
            val again = processor.repackage(stored, processor.probe(stored, maxDuration))
            // Then
            assertEquals(stored.contentHash, again.contentHash, name)
        }
    }

    @Test
    fun `Given a subtitle track beside VP9 and Opus, Then repackage writes WebM without it`() {
        val output = repackaged("subtitled.mkv")
        assertEquals("webm", containerOf(output))
        assertEquals(listOf("video", "audio"), tracksOf(output).map { it.getValue("codec_type") })
    }

    @Test
    fun `Given a repackaged video, Then its staged file carries its size and hash`() {
        val output = repackaged("h264-aac.mkv")
        val bytes = Files.readAllBytes(Path.of(output.path))
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        assertEquals(bytes.size.toLong() to hash, output.byteSize to output.contentHash)
        assertEquals(directory, Path.of(output.path).parent)
    }

    @Test
    fun `Given a video, Then poster is a PNG at the requested shortest side, in the proportions probe returns`() {
        for (name in listOf("h264-aac.mkv", "rotated.mp4", "anamorphic.mkv")) {
            // Given
            val source = copied(name)
            val video = processor.probe(source, maxDuration)
            // When
            val image = ImageIO.read(File(processor.poster(source, shortestSide = 60, fromOneFrame = false).path))
            // Then
            assertEquals(60, minOf(image.width, image.height), name)
            assertEquals(video.width * image.height, video.height * image.width, name)
        }
    }

    @Test
    fun `Given pixels twice as wide as tall, Then poster is twice as wide as the coded frame`() {
        val poster = processor.poster(copied("anamorphic.mkv"), shortestSide = 120, fromOneFrame = false)
        val image = ImageIO.read(File(poster.path))
        assertEquals(320 to 120, image.width to image.height)
    }

    @Test
    fun `Given a poster, Then its frames are scaled to the requested side before thumbnail holds them`() {
        // At 3840x2160, 1264 MB against 79 MB without thumbnail (lot 0.45.0's holistic review, peak RSS).
        val filters = processor.posterFilters(shortestSide = 60, fromOneFrame = false)
        assertTrue(filters.indexOf("scale=60:60") in 0..<filters.indexOf("thumbnail"), filters)
    }

    @Test
    fun `Given a poster from one frame, Then it holds no thumbnail and is drawn at the requested shortest side`() {
        assertFalse("thumbnail" in processor.posterFilters(shortestSide = 60, fromOneFrame = true))
        val image = ImageIO.read(File(processor.poster(copied("h264-aac.mkv"), 60, fromOneFrame = true).path))
        assertEquals(80 to 60, image.width to image.height)
    }

    @Test
    fun `Given a poster or a preview, Then its decoder and its filters are bounded to two threads, before -i`() {
        val command = processor.renderCommand("in.mkv", listOf("-frames:v", "1"), "out.png")
        val inputOptions = command.subList(0, command.indexOf("-i"))
        assertEquals(listOf("-threads", "2"), inputOptions.takeLast(2), command.toString())
        assertTrue(listOf("-filter_threads", "2") in inputOptions.windowed(2), command.toString())
    }

    @Test
    fun `Given an H265 tagged hev1, Then probe finds it not yet repackaged, and its repackaging already repackaged`() {
        assertEquals(false, processor.probe(staged("h265-hev1-aac.mov"), maxDuration).alreadyRepackaged)
        val output = repackaged("h265-hev1-aac.mov")
        assertEquals(true, processor.probe(output, maxDuration).alreadyRepackaged)
    }

    @Test
    fun `Given a long video, Then preview is an animated WebP of at most three seconds at the requested size`() {
        // When
        val preview = processor.preview(copied("too-long.mkv"), shortestSide = 24)
        val (canvas, durations) = animationOf(preview)
        // Then
        assertEquals(32 to 24, canvas)
        assertTrue(durations.size > 1, "frames: $durations")
        assertTrue(durations.sum() <= 3_000, "frames: $durations")
    }

    @Test
    fun `Given an MPEG-TS or an HLS playlist, Then repackage, poster and preview refuse it and leave nothing`() {
        // Given
        val playlist = copied("playlist.m3u8")
        val segment = copied("mpegts.ts")
        val video = processor.probe(staged("h264-aac.mkv"), maxDuration)
        for (refused in listOf(segment, playlist)) {
            // Then
            assertThrows(UndecodableVideoException::class.java) { processor.repackage(refused, video) }
            assertThrows(UndecodableVideoException::class.java) { processor.poster(refused, 24, fromOneFrame = false) }
            assertThrows(UndecodableVideoException::class.java) { processor.preview(refused, 24) }
        }
        val left = Files.list(directory).use { files -> files.map(Path::toString).toList() }
        assertEquals(setOf(playlist.path, segment.path), left.toSet())
    }

    @Test
    fun `Given H264 with AAC in Matroska, Then probe returns its codecs, dimensions and duration`() {
        // When
        val result = processor.probe(staged("h264-aac.mkv"), maxDuration)
        // Then
        assertEquals(VideoCodec.H264, result.videoCodec)
        assertEquals(AudioCodec.AAC, result.audioCodec)
        assertEquals(160 to 120, result.width to result.height)
        assertEquals(Duration.ofMillis(1_023), result.duration)
        assertEquals("avc1.64000A,mp4a.40.2", result.codecs)
    }

    @Test
    fun `Given H265 tagged hev1 with AAC in QuickTime, Then probe names it hvc1 from its configuration record`() {
        val result = processor.probe(staged("h265-hev1-aac.mov"), maxDuration)
        assertEquals(VideoCodec.H265, result.videoCodec)
        assertEquals("hvc1.1.6.L30.90,mp4a.40.2", result.codecs)
    }

    @Test
    fun `Given VP9 with Opus in WebM, Then probe spells Opus as WebM does`() {
        val result = processor.probe(staged("vp9-opus.webm"), maxDuration)
        assertEquals(AudioCodec.OPUS, result.audioCodec)
        assertEquals("vp09.00.10.08,opus", result.codecs)
    }

    @Test
    fun `Given AV1 without audio in MP4, Then probe returns no audio codec`() {
        val result = processor.probe(staged("av1.mp4"), maxDuration)
        assertEquals(VideoCodec.AV1, result.videoCodec)
        assertNull(result.audioCodec)
        assertEquals("av01.0.00M.08", result.codecs)
    }

    @Test
    fun `Given a video rotated a quarter turn, Then probe swaps its width and height`() {
        val result = processor.probe(staged("rotated.mp4"), maxDuration)
        assertEquals(120 to 160, result.width to result.height)
    }

    @Test
    fun `Given H264 with AC-3, Then probe refuses it naming the codec`() {
        val exception =
            assertThrows(VideoCodecUnsupportedException::class.java) {
                processor.probe(staged("h264-ac3.mkv"), maxDuration)
            }
        assertTrue(exception.message.orEmpty().contains("ac3"))
    }

    @Test
    fun `Given a clip of 121 seconds, Then probe refuses it as too long`() {
        assertThrows(VideoTooLongException::class.java) {
            processor.probe(staged("too-long.mkv"), maxDuration)
        }
    }

    @Test
    fun `Given an MPEG-TS, Then probe refuses it at opening`() {
        assertThrows(UndecodableVideoException::class.java) {
            processor.probe(staged("mpegts.ts"), maxDuration)
        }
    }

    @Test
    fun `Given an HLS playlist pointing at a local file, Then probe refuses it at opening`() {
        assertThrows(UndecodableVideoException::class.java) {
            processor.probe(staged("playlist.m3u8"), maxDuration)
        }
    }

    @Test
    fun `Given an AVIF still, Then probe refuses it`() {
        assertThrows(UndecodableVideoException::class.java) {
            processor.probe(staged("still.avif"), maxDuration)
        }
    }

    @Test
    fun `Given a timeout ffprobe cannot meet, Then the process is destroyed and reported`() {
        // Given
        val impatient = FfmpegVideoProcessor(Duration.ZERO, DECODER_MEMORY, webpQuality = 75)
        // When
        assertThrows(VideoProcessorTimeoutException::class.java) {
            impatient.probe(staged("h264-aac.mkv"), maxDuration)
        }
        // Then
        assertEquals(0, ProcessHandle.current().children().count())
    }

    @Test
    fun `Given an address space ffmpeg cannot start in, Then the poster is reported undecodable`() {
        val starved = FfmpegVideoProcessor(Duration.ofSeconds(60), maxAddressSpace = 1024 * 1024, webpQuality = 75)
        assertThrows(UndecodableVideoException::class.java) {
            starved.poster(copied("h264-aac.mkv"), shortestSide = 60, fromOneFrame = true)
        }
    }

    @Test
    fun `Given a staged video, Then probe reports its size as the store measured it`() {
        // Given: the bounds are MediaLimits' to compare, so the staged size is reported as it is
        val staged = staged("h264-aac.mkv").copy(byteSize = Long.MAX_VALUE)
        // When / Then
        assertEquals(Long.MAX_VALUE, processor.probe(staged, maxDuration).bytes)
    }

    private companion object {
        const val DECODER_MEMORY = 2L * 1024 * 1024 * 1024
    }
}
