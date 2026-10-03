package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** The cases no local page reaches, written as the JSON of `yt-dlp --dump-single-json`. */
class YtDlpReportTest {
    private val mapper = ObjectMapper()

    private fun video(vararg fields: Pair<String, Any?>) = mapOf("_type" to "video", "format_id" to "hls-300") + fields

    private fun formatOf(report: Map<String, Any?>) = YtDlpReport.formatOf(mapper.writeValueAsString(report))

    @Test
    fun `Given a video, Then its format is the one to download`() {
        assertEquals("hls-300", formatOf(video()))
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
