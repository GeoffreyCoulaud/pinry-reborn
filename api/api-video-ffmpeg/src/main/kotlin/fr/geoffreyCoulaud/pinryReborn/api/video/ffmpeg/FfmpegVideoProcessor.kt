package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessOutcome
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessRunner
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * [VideoProcessor] running the `ffprobe` and `ffmpeg` on the `PATH`, each destroyed past [timeout] (ADR 0047) and
 * capped at [maxAddressSpace] bytes (ADR 0050), its previews encoded at [webpQuality].
 */
class FfmpegVideoProcessor(timeout: Duration, maxAddressSpace: Long, private val webpQuality: Int) : VideoProcessor {
    private val runner = ProcessRunner(timeout, maxAddressSpace)

    override fun probe(staged: StagedFile, maxDuration: Duration): VideoProbeResult {
        return FfprobeReport.read(run(PROBE + staged.path), maxDuration)
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

    override fun poster(staged: StagedFile, shortestSide: Int): StagedFile {
        val options = listOf("-vf", posterFilters(shortestSide), "-frames:v", "1", "-c:v", "png", "-f", "image2")
        return write(staged, options, ::renderCommand)
    }

    // Scaled first: thumbnail holds the hundred frames it chooses among, so their size is its memory.
    internal fun posterFilters(shortestSide: Int) = "$SQUARE_PIXELS,${scaleTo(shortestSide)},thumbnail=n=100"

    override fun preview(staged: StagedFile, shortestSide: Int): StagedFile {
        val filters = listOf("-t", "3", "-vf", "$SQUARE_PIXELS,fps=12,${scaleTo(shortestSide)}")
        val encoder = listOf("-c:v", "libwebp_anim", "-quality", "$webpQuality", "-loop", "0", "-f", "webp")
        return write(staged, filters + encoder, ::renderCommand)
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

        fun scaleTo(shortestSide: Int) = "scale=$shortestSide:$shortestSide:force_original_aspect_ratio=increase"
    }
}
