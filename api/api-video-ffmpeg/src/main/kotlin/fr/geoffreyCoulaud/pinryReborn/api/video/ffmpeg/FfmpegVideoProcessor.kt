package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FrameSampler
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.LumaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits.Companion.MAX_FRAME_SIDE
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.PpmReader
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessOutcome
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessRunner
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.Locale

/**
 * [VideoProcessor] and a video's [FrameSampler] running the `ffprobe` and `ffmpeg` on the `PATH`, each destroyed past
 * [timeout] (ADR 0047) and capped at [maxAddressSpace] bytes (ADR 0050), its previews encoded at [webpQuality].
 */
class FfmpegVideoProcessor(timeout: Duration, maxAddressSpace: Long, private val webpQuality: Int) :
    VideoProcessor,
    FrameSampler {
    private val runner = ProcessRunner(timeout, maxAddressSpace)

    override fun probe(staged: StagedFile, maxDuration: Duration): VideoProbeResult {
        return FfprobeReport.read(run(PROBE + staged.path), maxDuration, staged.byteSize)
    }

    override fun repackage(staged: StagedFile, video: VideoProbeResult): StagedFile {
        val container =
            when (VideoContainer.of(video.videoCodec, video.audioCodec)) {
                VideoContainer.WEBM -> listOf("-f", "webm")
                VideoContainer.MP4 -> listOf("-movflags", "+faststart", "-f", "mp4")
            }
        val tag = if (video.videoCodec == VideoCodec.H265) listOf("-tag:v", "hvc1") else emptyList()
        // Without bitexact, Matroska writes random identifiers and two repackagings of one file differ; without
        // dropping the metadata, a source's tags are reordered on each pass, so a stored WebM repackaged again would.
        val copy =
            listOf("-map", "0:v:0", "-map", "0:a:0?", "-map_metadata", "-1", "-c", "copy", "-fflags", "+bitexact")
        return write(staged, copy + tag + container, ::copyCommand)
    }

    override fun poster(staged: StagedFile, shortestSide: Int, fromOneFrame: Boolean): StagedFile {
        val filters = posterFilters(shortestSide, fromOneFrame)
        val options = listOf("-vf", filters, "-frames:v", "1", "-c:v", "png", "-f", "image2")
        return write(staged, options, ::renderCommand)
    }

    // Scaled first: thumbnail holds the hundred frames it chooses among, so their size is its memory.
    internal fun posterFilters(shortestSide: Int, fromOneFrame: Boolean): String {
        val scaled = "$SQUARE_PIXELS,${scaleTo(shortestSide)}"
        return if (fromOneFrame) scaled else "$scaled,thumbnail=n=100"
    }

    override fun preview(staged: StagedFile, shortestSide: Int): StagedFile {
        val filters = listOf("-t", "3", "-vf", "$SQUARE_PIXELS,fps=12,${scaleTo(shortestSide)}")
        val encoder = listOf("-c:v", "libwebp_anim", "-quality", "$webpQuality", "-loop", "0", "-f", "webp")
        return write(staged, filters + encoder, ::renderCommand)
    }

    // One pass writes a PPM per frame beside the input, each read and deleted before the next is handed over.
    override fun sample(media: Media, staged: StagedFile, onFrame: (LumaFrame) -> Unit) {
        val directory = Files.createTempDirectory(Path.of(staged.path).toAbsolutePath().parent, "frames-")
        try {
            val instants = FrameSampler.instants(media.duration ?: Duration.ZERO)
            val select = instants.joinToString("+", transform = ::firstFrameAt)
            val filters = listOf("-map", "0:v:0", "-vf", "select='$select',$SQUARE_PIXELS,$BOUNDED_FRAME")
            val frames = listOf("-fps_mode", "passthrough", "-pix_fmt", "rgb24", "-f", "image2")
            run(renderCommand(staged.path, filters + frames, directory.resolve("%06d.ppm").toString()))
            for (frame in Files.list(directory).use { it.sorted().toList() }) {
                val raster = PpmReader.read(frame, MAX_FRAME_SIDE)
                Files.delete(frame)
                onFrame(LumaFrame.ofRgb(raster.width, raster.height, raster.rgb))
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun copyCommand(input: String, options: List<String>, output: String) =
        FFMPEG + listOf("-i", input) + options + output

    // One decoder thread per core holds its own frames: a 4K poster peaked at 663 MB on 12 cores, 331 MB on two.
    // The filter graph's threads grow with the cores the same way (ADR 0050).
    internal fun renderCommand(input: String, options: List<String>, output: String) =
        FFMPEG + listOf("-filter_threads", "2", "-threads", "2", "-i", input) + options + output

    // The broad catch rethrows: any failure, a timeout included, first removes the output beside the input.
    @Suppress("TooGenericExceptionCaught")
    private fun write(
        input: StagedFile,
        options: List<String>,
        command: (String, List<String>, String) -> List<String>,
    ): StagedFile {
        val output = Files.createTempFile(Path.of(input.path).toAbsolutePath().parent, "video-", ".tmp")
        try {
            run(command(input.path, options, output.toString()))
            return stagedAt(output)
        } catch (error: Throwable) {
            Files.deleteIfExists(output)
            throw error
        }
    }

    private fun stagedAt(path: Path): StagedFile {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { DigestInputStream(it, digest).transferTo(OutputStream.nullOutputStream()) }
        return StagedFile(path.toString(), Files.size(path), HexFormat.of().formatHex(digest.digest()))
    }

    private fun run(command: List<String>): String =
        when (val outcome = runner.run(command)) {
            is ProcessOutcome.TimedOut ->
                throw VideoProcessorTimeoutException("${command.first()} ran past ${outcome.timeout} and was destroyed")
            is ProcessOutcome.Exited ->
                if (outcome.code == 0) {
                    outcome.output
                } else {
                    throw UndecodableVideoException("${command.first()} refused the file: ${outcome.errors.trim()}")
                }
        }

    private companion object {
        // Before -i, where ffmpeg reads them as input options: after it, an MPEG-TS would pass (ADR 0047, decision 3).
        val DEMUXERS = listOf("-format_whitelist", "mov,matroska", "-protocol_whitelist", "file")
        val PROBE =
            listOf("ffprobe", "-v", "error") + DEMUXERS +
                listOf("-of", "json", "-show_streams", "-show_format", "-show_data", "-count_packets", "-i")
        val FFMPEG = listOf("ffmpeg", "-nostdin", "-y", "-v", "error") + DEMUXERS

        // An anamorphic source's pixels made square, so a rendition keeps the proportions the video displays at.
        const val SQUARE_PIXELS = "scale=iw*sar:ih,setsar=1"

        // Downscaled to fit the bound, never upscaled.
        const val BOUNDED_FRAME =
            "scale='min($MAX_FRAME_SIDE,iw)':'min($MAX_FRAME_SIDE,ih)':force_original_aspect_ratio=decrease"
        const val MILLIS_PER_SECOND = 1000.0

        fun scaleTo(shortestSide: Int) = "scale=$shortestSide:$shortestSide:force_original_aspect_ratio=increase"

        // Selects the first frame at or after [instant].
        fun firstFrameAt(instant: Duration): String {
            val seconds = "%.3f".format(Locale.ROOT, instant.toMillis() / MILLIS_PER_SECOND)
            return "gte(t,$seconds)*not(gte(prev_t,$seconds))"
        }
    }
}
