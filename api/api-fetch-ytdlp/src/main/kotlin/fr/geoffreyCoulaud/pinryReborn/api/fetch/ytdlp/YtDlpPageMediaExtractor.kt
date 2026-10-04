package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchedMedia
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaExtractor
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.GuardingProxy
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessOutcome
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessRunner
import java.io.FilterInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * [PageMediaExtractor] running the `yt-dlp` on the `PATH` twice per page through its own [GuardingProxy], each run
 * destroyed past [timeout] or once its directory under [stagingDirectory] passes [maxBytes] (ADR 0048, decision 3).
 */
class YtDlpPageMediaExtractor(
    private val stagingDirectory: Path,
    private val maxBytes: Long,
    private val maxDuration: Duration,
    timeout: Duration,
    private val openProxy: () -> GuardingProxy,
) : PageMediaExtractor {
    private val runner = ProcessRunner(timeout)

    // The broad catch rethrows: any failure, a lost lease included, first deletes what the runs wrote.
    @Suppress("TooGenericExceptionCaught")
    override fun extract(pageUrl: String, heartbeat: () -> Unit): FetchedMedia {
        val directory = Files.createTempDirectory(Files.createDirectories(stagingDirectory), "yt-dlp-")
        try {
            val file = openProxy().use { Extraction(pageUrl, directory, it, heartbeat).download() }
            return FetchedMedia(DeletingStream(file, directory), contentType = null)
        } catch (error: Throwable) {
            directory.toFile().deleteRecursively()
            throw error
        }
    }

    // One extraction: both runs share its proxy and its directory, where a hostile concatenation finds the report.
    private inner class Extraction(
        private val pageUrl: String,
        private val directory: Path,
        private val proxy: GuardingProxy,
        private val heartbeat: () -> Unit,
    ) {
        private val options = OPTIONS + listOf("--proxy", "http://${proxy.address.hostString}:${proxy.address.port}")

        // The second run reads the first's report, not the page, which could offer other formats the second time.
        fun download(): Path {
            val report = run(listOf("-f", FORMATS, "--dump-single-json", "--", pageUrl))
            val format = YtDlpReport.formatOf(report, maxDuration, maxBytes)
            Files.writeString(directory.resolve(INFO_FILE), YtDlpReport.infoOf(report))
            return directory.resolve(run(listOf("--load-info-json", INFO_FILE, "-f", format) + DOWNLOAD).trim())
        }

        private fun run(arguments: List<String>): String =
            when (val outcome = runner.run(listOf("yt-dlp") + options + arguments, directory, ::tick)) {
                is ProcessOutcome.TimedOut ->
                    throw FetchUnreachableException("yt-dlp ran past ${outcome.timeout} and was destroyed")
                is ProcessOutcome.Exited ->
                    if (outcome.code == 0) {
                        outcome.output
                    } else {
                        val reason = outcome.errors.trim()
                        throw proxy.refusal() ?: NoMediaFoundException("yt-dlp found no media: $reason")
                    }
            }

        private fun tick() {
            heartbeat()
            if (directory.toFile().walk().sumOf { it.length() } > maxBytes) {
                throw FetchTooLargeException("yt-dlp wrote past $maxBytes bytes and was destroyed")
            }
        }
    }

    // Closing the extracted file ends the extraction: its directory goes with it.
    private class DeletingStream(file: Path, private val directory: Path) :
        FilterInputStream(Files.newInputStream(file)) {
        override fun close() =
            try {
                super.close()
            } finally {
                directory.toFile().deleteRecursively()
            }
    }

    internal companion object {
        const val DEMUXERS = "-nostdin -format_whitelist mov,matroska,mpegts -protocol_whitelist file"

        // ADR 0047 decision 3, mpegts added for the HLS fixup. A bare `ffmpeg_i` reaches no postprocessor.
        val POSTPROCESSORS =
            listOf("Merger", "FixupM3u8", "FixupM4a", "FixupStretched", "FixupDuplicateMoov", "FixupTimestamp") +
                "FixupDuration"

        // The API's home is root-owned: yt-dlp reads and writes nothing of its own there. The native downloader
        // keeps an HLS download in yt-dlp's own networking, through `--proxy`, rather than in an ffmpeg.
        val OPTIONS =
            listOf("--ignore-config", "--no-plugin-dirs", "--no-cache-dir", "--no-playlist", "--playlist-items", "1") +
                listOf("--downloader", "native") +
                POSTPROCESSORS.flatMap { listOf("--postprocessor-args", "$it+ffmpeg_i:$DEMUXERS") }

        const val INFO_FILE = "info.json"

        val DOWNLOAD = listOf("-o", "media.%(ext)s", "--print", "after_move:filepath")

        // The protocols yt-dlp downloads itself, through `--proxy`: rtmp, rtsp or mms would go to an external program.
        private const val PROXIED = "[protocol~='^(https?|m3u8(_native)?|http_dash_segments)$']"

        // H.264, then VP9, then AV1, then H.265, each with an accepted audio codec (ADR 0048, decision 3); then
        // whatever declares no codec, as a bare `<video src>` does, which ingestion judges.
        val FORMATS =
            listOf("^(avc|h264)", "^vp0?9", "^av01", "^(hvc1|hev1|h265)").joinToString("/") { video ->
                val audio = "[acodec~='^(mp4a|opus|mp3)']"
                "bv$PROXIED[vcodec~='$video']+ba$PROXIED$audio/b$PROXIED[vcodec~='$video']$audio"
            } + "/b$PROXIED"
    }
}
