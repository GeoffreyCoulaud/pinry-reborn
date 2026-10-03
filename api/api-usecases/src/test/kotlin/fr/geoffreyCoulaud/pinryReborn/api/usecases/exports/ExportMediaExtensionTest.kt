package fr.geoffreyCoulaud.pinryReborn.api.usecases.exports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ExportMediaExtensionTest {
    @ParameterizedTest
    @CsvSource(
        "image/jpeg,jpg",
        "image/png,png",
        "image/webp,webp",
        "image/gif,gif",
        "image/avif,avif",
        "'video/mp4; codecs=\"avc1.640015,mp4a.40.2\"',mp4",
        "'video/webm; codecs=\"vp09.00.10.08,opus\"',webm",
        "application/x-thing,bin",
    )
    fun `Given a mime type, Then the archive extension matches`(mimeType: String, expected: String) {
        // Given
        // mimeType and expected are supplied by @CsvSource, covering every when-branch plus the else fallback.

        // When
        val extension = ExportMediaExtension.forMimeType(mimeType)

        // Then
        assertEquals(expected, extension)
    }
}
