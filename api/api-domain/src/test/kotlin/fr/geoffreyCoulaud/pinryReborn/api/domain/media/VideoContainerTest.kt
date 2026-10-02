package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoContainerTest {
    @Test
    fun `Given every pair of codecs, Then only VP9 or AV1 with Opus or no audio lands in WebM`() {
        // Given
        val audioCodecs = AudioCodec.entries + null
        val webm =
            setOf(
                VideoCodec.VP9 to AudioCodec.OPUS,
                VideoCodec.VP9 to null,
                VideoCodec.AV1 to AudioCodec.OPUS,
                VideoCodec.AV1 to null,
            )
        for (video in VideoCodec.entries) {
            for (audio in audioCodecs) {
                // When
                val container = VideoContainer.of(video, audio)
                // Then
                val expected = if (video to audio in webm) VideoContainer.WEBM else VideoContainer.MP4
                assertEquals(expected, container, "$video with $audio")
            }
        }
    }
}
