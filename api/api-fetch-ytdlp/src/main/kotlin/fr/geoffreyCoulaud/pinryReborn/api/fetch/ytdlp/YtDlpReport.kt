package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException

/** Reads the JSON of `yt-dlp --dump-single-json`, the first of the two runs (decision v). */
internal object YtDlpReport {
    private val mapper = ObjectMapper()

    /** The format to download. */
    fun formatOf(json: String): String {
        val report = mapper.readTree(json)
        // A page of several videos is a playlist, of which `--playlist-items 1` keeps the first.
        val video = report.path("entries").path(0).takeUnless(JsonNode::isMissingNode) ?: report
        return video.path("format_id").textValue() ?: throw NoMediaFoundException("The page offers no format")
    }
}
