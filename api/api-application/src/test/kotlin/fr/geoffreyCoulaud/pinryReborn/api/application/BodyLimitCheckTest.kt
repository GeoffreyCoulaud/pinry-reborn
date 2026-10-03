package fr.geoffreyCoulaud.pinryReborn.api.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/** No boot: a refused configuration could not start the instance this module's suites share. */
class BodyLimitCheckTest {
    @Test
    fun `Given every upload limit under the body limit, Then the boot goes on`() {
        // Given / When / Then
        assertDoesNotThrow {
            BodyLimitCheck.verify(maxImageBytes = 99, maxVideoBytes = 99, maxChunkBytes = 99, maxBodyBytes = 100)
        }
    }

    @Test
    fun `Given an image limit equal to the body limit, Then the boot is refused naming both keys`() {
        // Given / When: equal leaves no room for the multipart framing around the file
        val error = assertThrows<IllegalStateException> {
            BodyLimitCheck.verify(maxImageBytes = 100, maxVideoBytes = 99, maxChunkBytes = 99, maxBodyBytes = 100)
        }

        // Then
        assertEquals(
            "media.max_image_bytes (100) must be strictly under quarkus.http.limits.max-body-size (100)",
            error.message,
        )
    }

    @Test
    fun `Given a video limit past the body limit, Then the boot is refused naming both keys`() {
        // Given / When
        val error = assertThrows<IllegalStateException> {
            BodyLimitCheck.verify(maxImageBytes = 99, maxVideoBytes = 101, maxChunkBytes = 99, maxBodyBytes = 100)
        }

        // Then
        assertEquals(
            "media.max_video_bytes (101) must be strictly under quarkus.http.limits.max-body-size (100)",
            error.message,
        )
    }

    @Test
    fun `Given a chunk limit past the body limit, Then the boot is refused naming both keys`() {
        // Given / When
        val error = assertThrows<IllegalStateException> {
            BodyLimitCheck.verify(maxImageBytes = 99, maxVideoBytes = 99, maxChunkBytes = 101, maxBodyBytes = 100)
        }

        // Then
        assertEquals(
            "imports.max_chunk_bytes (101) must be strictly under quarkus.http.limits.max-body-size (100)",
            error.message,
        )
    }
}
