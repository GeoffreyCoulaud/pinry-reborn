package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.CancelTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClearPinDownloadTest {
    private val downloads: MediaDownloadRepositoryInterface = mockk(relaxed = true)
    private val cancelTask: CancelTask = mockk(relaxed = true)
    private val pinId = randomUUID()
    private val sourceUrl = checkNotNull(HttpUrl.parse("https://x"))
    private val subject = ClearPinDownload(downloads, cancelTask)

    @Test
    fun `Given a download row, Then it cancels the task and deletes the row`() {
        val taskId = randomUUID()
        every { downloads.findByPinId(pinId) } returns
            MediaDownload(pinId, sourceUrl, DownloadStatus.PENDING, null, taskId, Instant.EPOCH, Instant.EPOCH)
        val result = subject.clear(pinId)
        assertTrue(result)
        verify { cancelTask.cancel(taskId) }
        verify { downloads.deleteByPinId(pinId) }
    }

    @Test
    fun `Given a cancellation that fails, Then the failure reaches the caller and the row stays`() {
        // Given: a failed statement aborts the caller's transaction (ADR 0059, decision 10)
        val taskId = randomUUID()
        every { downloads.findByPinId(pinId) } returns
            MediaDownload(pinId, sourceUrl, DownloadStatus.PENDING, null, taskId, Instant.EPOCH, Instant.EPOCH)
        every { cancelTask.cancel(taskId) } throws IllegalStateException("current transaction is aborted")

        // When, Then
        assertThrows<IllegalStateException> { subject.clear(pinId) }
        verify(exactly = 0) { downloads.deleteByPinId(any()) }
    }

    @Test
    fun `Given no download row, Then it does nothing`() {
        every { downloads.findByPinId(pinId) } returns null
        val result = subject.clear(pinId)
        assertFalse(result)
        verify(exactly = 0) { cancelTask.cancel(any()) }
        verify(exactly = 0) { downloads.deleteByPinId(any()) }
    }
}
