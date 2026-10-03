package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaTooLongException
import java.time.Duration

/** Reads the JSON of `yt-dlp --dump-single-json`, the first of the two runs (ADR 0048, decision 3). */
internal object YtDlpReport {
    private val mapper = ObjectMapper()
    private const val MILLIS_PER_SECOND = 1_000

    /** The format to download, or the refusal its duration, its size or its live flag earns. */
    @Suppress("ThrowsCount") // Each throw is a distinct refusal the first run decides, which is this object's purpose.
    fun formatOf(json: String, maxDuration: Duration, maxBytes: Long): String {
        val video = videoOf(json)
        if (video.path("is_live").asBoolean()) throw NoMediaFoundException("The page shows a live stream")
        val duration = Duration.ofMillis((video.path("duration").asDouble() * MILLIS_PER_SECOND).toLong())
        if (duration > maxDuration) throw PageMediaTooLongException("The page's video lasts $duration")
        // A merged format carries the sum of its parts as an approximate size.
        val size = video.path("filesize").asLong(video.path("filesize_approx").asLong())
        if (size > maxBytes) throw FetchTooLargeException("The page's video weighs $size bytes")
        return video.path("format_id").textValue() ?: throw NoMediaFoundException("The page offers no format")
    }

    /**
     * The chosen video for `--load-info-json`, less the page address yt-dlp would fetch again when a download fails.
     * A playlist loaded whole fails there with "There are no entries".
     */
    fun infoOf(json: String): String =
        mapper.writeValueAsString((videoOf(json) as ObjectNode).apply { remove("webpage_url") })

    // A page of several videos is a playlist, of which `--playlist-items 1` keeps the first.
    private fun videoOf(json: String): JsonNode {
        val report = mapper.readTree(json)
        return report.path("entries").path(0).takeUnless(JsonNode::isMissingNode) ?: report
    }
}
