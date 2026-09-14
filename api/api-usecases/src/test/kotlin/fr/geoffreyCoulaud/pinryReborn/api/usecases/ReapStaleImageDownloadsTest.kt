package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ReapStaleImageDownloadsTest : BaseTest() {
    private val imageDownloadRepository: ImageDownloadRepositoryInterface = mockk()
    private val clock: Clock = mockk()
    private val failedGrace = Duration.ofDays(7)
    private val pendingGrace = Duration.ofHours(1)

    private val reap = ReapStaleImageDownloads(
        imageDownloadRepository = imageDownloadRepository,
        clock = clock,
        failedGrace = failedGrace,
        pendingGrace = pendingGrace,
    )

    private val now = Instant.parse("2026-09-14T00:00:00Z")

    @Test
    fun `Given stale rows of both kinds, Then reap settles the pending ones and deletes the failed ones`() {
        // Given
        every { clock.now() } returns now
        every {
            imageDownloadRepository.failPendingBefore(now - pendingGrace, DownloadReason.INTERNAL_ERROR, now)
        } returns 2
        every { imageDownloadRepository.deleteFailedBefore(now - failedGrace) } returns 3

        // When
        val count = reap.reap()

        // Then: each cutoff is clock.now() minus its own grace, and the two counts are summed
        assertEquals(5, count)
        verify {
            imageDownloadRepository.failPendingBefore(now - pendingGrace, DownloadReason.INTERNAL_ERROR, now)
            imageDownloadRepository.deleteFailedBefore(now - failedGrace)
        }
    }

    @Test
    fun `Given no stale row, Then reap returns zero`() {
        // Given
        every { clock.now() } returns now
        every { imageDownloadRepository.failPendingBefore(any(), any(), any()) } returns 0
        every { imageDownloadRepository.deleteFailedBefore(any()) } returns 0

        // When
        val count = reap.reap()

        // Then
        assertEquals(0, count)
    }
}
