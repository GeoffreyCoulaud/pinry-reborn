package fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorTimeoutException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.time.Duration
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** [VideoProcessor] running the `ffprobe` and `ffmpeg` on the `PATH`, each destroyed past [timeout] (ADR 0047). */
class FfmpegVideoProcessor(private val timeout: Duration) : VideoProcessor {
    override fun probe(staged: StagedFile, maxDuration: Duration): VideoProbeResult {
        return FfprobeReport.read(run(PROBE + staged.path), maxDuration)
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
    }
}
