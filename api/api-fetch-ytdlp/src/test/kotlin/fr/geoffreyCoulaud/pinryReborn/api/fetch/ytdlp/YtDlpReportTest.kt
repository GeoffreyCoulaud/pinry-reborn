package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaTooLongException
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** The cases no local page reaches, written as the JSON of `yt-dlp --dump-single-json`. */
class YtDlpReportTest {
    private val mapper = ObjectMapper()
    private val maxDuration = Duration.ofSeconds(120)
    private val maxBytes = 1_000L

    private fun video(vararg fields: Pair<String, Any?>) = mapOf("_type" to "video", "format_id" to "hls-300") + fields

    private fun formatOf(report: Map<String, Any?>) =
        YtDlpReport.formatOf(mapper.writeValueAsString(report), maxDuration, maxBytes)

    @Test
    fun `Given a video, Then its format is the one to download`() {
        assertEquals("hls-300", formatOf(video()))
    }

    @Test
    fun `Given a video at both bounds, Then its format is the one to download`() {
        assertEquals("hls-300", formatOf(video("duration" to 120, "filesize" to 1_000, "filesize_approx" to null)))
    }

    @Test
    fun `Given a live stream, Then no media is found`() {
        assertThrows(NoMediaFoundException::class.java) { formatOf(video("is_live" to true)) }
    }

    @Test
    fun `Given a duration past the bound, Then the video is refused too long`() {
        assertThrows(PageMediaTooLongException::class.java) { formatOf(video("duration" to 120.5)) }
    }

    @Test
    fun `Given a file size past the bound, Then the video is refused too large`() {
        assertThrows(FetchTooLargeException::class.java) { formatOf(video("filesize" to 1_001)) }
    }

    @Test
    fun `Given only an approximate size past the bound, as a merged format, Then the video is refused too large`() {
        assertThrows(FetchTooLargeException::class.java) {
            formatOf(video("filesize" to null, "filesize_approx" to 1_001))
        }
    }

    @Test
    fun `Given a playlist, Then the format to download is its first entry's`() {
        assertEquals("0", formatOf(mapOf("_type" to "playlist", "entries" to listOf(video("format_id" to "0")))))
    }

    @Test
    fun `Given no chosen format, Then no media is found`() {
        val emptyPlaylist = mapOf("_type" to "playlist", "entries" to listOf<Any>())
        assertThrows(NoMediaFoundException::class.java) { formatOf(emptyPlaylist) }
    }
}
