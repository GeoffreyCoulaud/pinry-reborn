package fr.geoffreyCoulaud.pinryReborn.api.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/** No boot: a refused configuration could not start the instance this module's suites share. */
class OrphanGraceCheckTest {
    @Test
    fun `Given an orphan grace longer than the extraction timeout, Then the boot goes on`() {
        // Given / When / Then
        assertDoesNotThrow {
            OrphanGraceCheck.verify(orphanGrace = Duration.ofMinutes(6), extractionTimeout = Duration.ofMinutes(5))
        }
    }

    @Test
    fun `Given an orphan grace equal to the extraction timeout, Then the boot is refused naming both keys`() {
        // Given / When: equal lets the sweep reach a file the last instant of a run still writes
        val error = assertThrows<IllegalStateException> {
            OrphanGraceCheck.verify(orphanGrace = Duration.ofMinutes(5), extractionTimeout = Duration.ofMinutes(5))
        }

        // Then
        assertEquals(
            "garbage-collection.orphan_grace (PT5M) must be longer than media.download.extraction_timeout (PT5M)",
            error.message,
        )
    }

    @Test
    fun `Given an orphan grace shorter than the extraction timeout, Then the boot is refused naming both keys`() {
        // Given / When
        val error = assertThrows<IllegalStateException> {
            OrphanGraceCheck.verify(orphanGrace = Duration.ofMinutes(4), extractionTimeout = Duration.ofMinutes(5))
        }

        // Then
        assertEquals(
            "garbage-collection.orphan_grace (PT4M) must be longer than media.download.extraction_timeout (PT5M)",
            error.message,
        )
    }
}
