package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaTooLongException
import java.time.Duration

/** Reads the JSON of `yt-dlp --dump-single-json`, the first of the two runs (decision v). */
internal object YtDlpReport {
    private val mapper = ObjectMapper()
    private const val MILLIS_PER_SECOND = 1_000

    /** The format to download, or the refusal its duration, its size or its live flag earns. */
    @Suppress("ThrowsCount") // Each throw is a distinct refusal the first run decides, which is this object's purpose.
    fun formatOf(json: String, maxDuration: Duration, maxBytes: Long): String {
        val report = mapper.readTree(json)
        // A page of several videos is a playlist, of which `--playlist-items 1` keeps the first.
        val video = report.path("entries").path(0).takeUnless(JsonNode::isMissingNode) ?: report
        if (video.path("is_live").asBoolean()) throw NoMediaFoundException("The page shows a live stream")
        val duration = Duration.ofMillis((video.path("duration").asDouble() * MILLIS_PER_SECOND).toLong())
        if (duration > maxDuration) throw PageMediaTooLongException("The page's video lasts $duration")
        // A merged format carries the sum of its parts as an approximate size.
        val size = video.path("filesize").asLong(video.path("filesize_approx").asLong())
        if (size > maxBytes) throw FetchTooLargeException("The page's video weighs $size bytes")
        return video.path("format_id").textValue() ?: throw NoMediaFoundException("The page offers no format")
    }
}
