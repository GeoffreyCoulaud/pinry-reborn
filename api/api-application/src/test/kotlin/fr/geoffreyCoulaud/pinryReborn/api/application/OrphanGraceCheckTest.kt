package fr.geoffreyCoulaud.pinryReborn.api.application

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/** No boot: a refused configuration could not start the instance this module's suites share. */
class OrphanGraceCheckTest {
    @Test
    fun `Given an orphan grace longer than twice the extraction timeout, Then the boot goes on`() {
        // Given / When / Then
        assertDoesNotThrow {
            OrphanGraceCheck.verify(orphanGrace = Duration.ofMinutes(11), extractionTimeout = Duration.ofMinutes(5))
        }
    }

    @Test
    fun `Given an orphan grace equal to twice the extraction timeout, Then the boot is refused naming both keys`() {
        // Given / When: equal lets the sweep reach a file the last instant of the second run still writes
        val error =
            assertThrows<IllegalStateException> {
                OrphanGraceCheck.verify(orphanGrace = Duration.ofMinutes(10), extractionTimeout = Duration.ofMinutes(5))
            }

        // Then
        assertEquals(
            "garbage-collection.orphan_grace (PT10M) must be longer than twice " +
                "media.download.extraction_timeout (PT5M), one per yt-dlp run",
            error.message,
        )
    }

    @Test
    fun `Given an orphan grace between one and two extraction timeouts, Then the boot is refused naming both keys`() {
        // Given / When: one extraction runs yt-dlp twice, each run bounded by the timeout
        val error =
            assertThrows<IllegalStateException> {
                OrphanGraceCheck.verify(orphanGrace = Duration.ofMinutes(6), extractionTimeout = Duration.ofMinutes(5))
            }

        // Then
        assertEquals(
            "garbage-collection.orphan_grace (PT6M) must be longer than twice " +
                "media.download.extraction_timeout (PT5M), one per yt-dlp run",
            error.message,
        )
    }
}
