package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoContainer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** [VideoProcessor] running the `ffprobe` and `ffmpeg` on the `PATH`, each destroyed past [timeout] (ADR 0047). */
class FfmpegVideoProcessor(private val timeout: Duration) : VideoProcessor {
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
        // Without bitexact, Matroska writes random identifiers and two repackagings of one file differ.
        val copy = listOf("-map", "0:v:0", "-map", "0:a:0?", "-c", "copy", "-fflags", "+bitexact")
        return write(staged, copy + tag + container)
    }

    override fun poster(staged: StagedFile): StagedFile =
        write(staged, listOf("-vf", "thumbnail=n=100,$SQUARE_PIXELS", "-frames:v", "1", "-c:v", "png", "-f", "image2"))

    override fun preview(staged: StagedFile, shortestSide: Int, quality: Int): StagedFile {
        val scale = "scale=$shortestSide:$shortestSide:force_original_aspect_ratio=increase"
        val filters = listOf("-t", "3", "-vf", "$SQUARE_PIXELS,fps=12,$scale")
        val encoder = listOf("-c:v", "libwebp_anim", "-quality", "$quality", "-loop", "0", "-f", "webp")
        return write(staged, filters + encoder)
    }

    // The broad catch rethrows: any failure, a timeout included, first removes the output beside the input.
    @Suppress("TooGenericExceptionCaught")
    private fun write(input: StagedFile, options: List<String>): StagedFile {
        val output = Files.createTempFile(Path.of(input.path).toAbsolutePath().parent, "video-", ".tmp")
        try {
            run(FFMPEG + input.path + options + output.toString())
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

    private fun run(command: List<String>): String {
        val process = ProcessBuilder(command).start()
        val output = FutureTask { process.inputStream.readAllBytes() }.also { Thread.ofVirtual().start(it) }
        val errors = FutureTask { process.errorStream.readAllBytes() }.also { Thread.ofVirtual().start(it) }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly().waitFor()
            throw VideoProcessorTimeoutException("${command.first()} ran past $timeout and was destroyed")
        }
        if (process.exitValue() != 0) {
            val reason = errors.get().decodeToString().trim()
            throw UndecodableVideoException("${command.first()} refused the file: $reason")
        }
        return output.get().decodeToString()
    }

    private companion object {
        // Before -i, where ffmpeg reads them as input options: after it, an MPEG-TS would pass (decision J1).
        val DEMUXERS = listOf("-format_whitelist", "mov,matroska", "-protocol_whitelist", "file")
        val PROBE =
            listOf("ffprobe", "-v", "error") + DEMUXERS +
                listOf("-of", "json", "-show_streams", "-show_format", "-show_data", "-count_packets", "-i")
        val FFMPEG = listOf("ffmpeg", "-nostdin", "-y", "-v", "error") + DEMUXERS + "-i"

        // An anamorphic source's pixels made square, so a rendition keeps the proportions the video displays at.
        const val SQUARE_PIXELS = "scale=iw*sar:ih,setsar=1"
    }
}
