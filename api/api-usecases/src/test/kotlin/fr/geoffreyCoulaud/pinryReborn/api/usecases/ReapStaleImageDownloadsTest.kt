package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.ImageDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class ReapStaleImageDownloadsTest : BaseTest() {
    private val imageDownloadRepository: ImageDownloadRepositoryInterface = mockk()
    private val taskQueue: TaskQueueInterface = mockk()
    private val clock: Clock = mockk()
    private val failedGrace = Duration.ofDays(7)

    private val reap = ReapStaleImageDownloads(
        imageDownloadRepository = imageDownloadRepository,
        taskQueue = taskQueue,
        clock = clock,
        failedGrace = failedGrace,
    )

    private val now = Instant.parse("2026-09-14T00:00:00Z")

    private fun pending(pinId: UUID, taskId: UUID) = ImageDownload(
        pinId = pinId, sourceUrl = "https://x/i.png", status = DownloadStatus.PENDING, reasonCode = null,
        lastError = null, taskId = taskId, requestedAt = now, updatedAt = now,
    )

    @Test
    fun `Given a PENDING row whose task is gone, Then reap settles that row and leaves the live one`() {
        // Given: two running downloads, and only one of the two tasks is still live
        val abandoned = pending(randomUUID(), randomUUID())
        val running = pending(randomUUID(), randomUUID())
        every { clock.now() } returns now
        every { imageDownloadRepository.findPending() } returns listOf(abandoned, running)
        every { taskQueue.findLiveIds(listOf(abandoned.taskId, running.taskId)) } returns setOf(running.taskId)
        every { imageDownloadRepository.markFailed(abandoned.pinId, DownloadReason.INTERNAL_ERROR, now) } returns true
        every { imageDownloadRepository.deleteFailedBefore(now - failedGrace) } returns 0

        // When
        val count = reap.reap()

        // Then: the abandoned row is settled, and nothing touches the one whose task still lives
        assertEquals(1, count)
        verify(exactly = 0) { imageDownloadRepository.markFailed(running.pinId, any(), any()) }
    }

    @Test
    fun `Given more pending rows than one lookup holds, Then reap asks the queue one batch at a time`() {
        // Given: one row past the batch, so an unchunked call would spend 501 host parameters
        val rows = (0..BATCH_SIZE).map { pending(randomUUID(), randomUUID()) }
        val batches = mutableListOf<Collection<UUID>>()
        every { clock.now() } returns now
        every { imageDownloadRepository.findPending() } returns rows
        every { taskQueue.findLiveIds(capture(batches)) } answers { firstArg<Collection<UUID>>().toSet() }
        every { imageDownloadRepository.deleteFailedBefore(now - failedGrace) } returns 0

        // When
        reap.reap()

        // Then: two lookups, neither past the batch, and between them every task id was asked about
        assertEquals(listOf(BATCH_SIZE, 1), batches.map { it.size })
        assertEquals(rows.map { it.taskId }.toSet(), batches.flatten().toSet())
    }

    @Test
    fun `Given a row another writer settled first, Then its refused CAS is not counted`() {
        // Given: markFailed is a CAS on PENDING, and the row stopped being PENDING between the read
        // and the write
        val raced = pending(randomUUID(), randomUUID())
        every { clock.now() } returns now
        every { imageDownloadRepository.findPending() } returns listOf(raced)
        every { taskQueue.findLiveIds(listOf(raced.taskId)) } returns emptySet()
        every { imageDownloadRepository.markFailed(raced.pinId, DownloadReason.INTERNAL_ERROR, now) } returns false
        every { imageDownloadRepository.deleteFailedBefore(now - failedGrace) } returns 0

        // When / Then
        assertEquals(0, reap.reap())
    }

    @Test
    fun `Given no PENDING row, Then reap still deletes the settled ones past the grace`() {
        // Given
        every { clock.now() } returns now
        every { imageDownloadRepository.findPending() } returns emptyList()
        every { imageDownloadRepository.deleteFailedBefore(now - failedGrace) } returns 3

        // When / Then: no pending row is no chunk, so the queue is not asked at all
        assertEquals(3, reap.reap())
        verify(exactly = 0) { taskQueue.findLiveIds(any()) }
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
