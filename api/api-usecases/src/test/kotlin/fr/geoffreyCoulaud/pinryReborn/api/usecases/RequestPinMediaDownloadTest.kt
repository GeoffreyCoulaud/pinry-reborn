package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.Task
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.TaskState
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaSourceUrlInvalidError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.PinDownloadTask
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class RequestPinMediaDownloadTest {
    private val pins: PinRepositoryInterface = mockk()
    private val downloads: MediaDownloadRepositoryInterface = mockk(relaxed = true)
    private val enqueue: EnqueueTask = mockk()
    private val runner: TransactionRunner = mockk()
    private val clock: Clock = mockk()
    private val now = Instant.parse("2026-07-10T00:00:00Z")
    private val owner = User(randomUUID(), "o", createdAt = TestTime.now)
    private val pinId = randomUUID()

    private val subject = RequestPinMediaDownload(pins, downloads, enqueue, runner, clock)

    init {
        every { clock.now() } returns now
        every { runner.inTransaction<MediaDownload>(any()) } answers { firstArg<() -> MediaDownload>().invoke() }
    }

    private fun pin(author: User = owner) = Pin(pinId, author, "https://ctx", null, "d", emptyList(), emptyList(),
        createdAt = TestTime.now, updatedAt = TestTime.now)
    private fun aTask(id: java.util.UUID) = Task(
        id, PinDownloadTask.KIND, pinId.toString(), TaskState.PENDING, 0, now, 0, 5, null, null, false,
        "${PinDownloadTask.KIND}:$pinId", null,
    )

    @Test
    fun `Given a missing pin, Then it throws MediaPinDoesNotExistError`() {
        every { pins.findPinById(pinId) } returns null
        assertThrows(MediaPinDoesNotExistError::class.java) { subject.request(pinId, owner, "https://x/i.png") }
    }

    @Test
    fun `Given a non-owner, Then it throws MediaPermissionError`() {
        every { pins.findPinById(pinId) } returns pin(author = User(randomUUID(), "other", createdAt = TestTime.now))
        assertThrows(MediaPermissionError::class.java) { subject.request(pinId, owner, "https://x/i.png") }
    }

    @Test
    fun `Given a non-http url, Then it throws MediaSourceUrlInvalidError`() {
        every { pins.findPinById(pinId) } returns pin()
        assertThrows(MediaSourceUrlInvalidError::class.java) { subject.request(pinId, owner, "ftp://x/i.png") }
    }

    @Test
    fun `Given a schemeless url, Then it throws MediaSourceUrlInvalidError`() {
        every { pins.findPinById(pinId) } returns pin()
        assertThrows(MediaSourceUrlInvalidError::class.java) { subject.request(pinId, owner, "not-a-url") }
    }

    @Test
    fun `Given a malformed url, Then it throws MediaSourceUrlInvalidError`() {
        every { pins.findPinById(pinId) } returns pin()
        assertThrows(MediaSourceUrlInvalidError::class.java) { subject.request(pinId, owner, "http://exa mple/i.png") }
    }

    @Test
    fun `Given a plain http url, Then it enqueues pin download and upserts a PENDING row atomically`() {
        val taskId = randomUUID()
        every { pins.findPinById(pinId) } returns pin()
        every { enqueue.enqueue(any(), any(), any(), any(), any(), any()) } returns aTask(taskId)
        every { downloads.upsertPending(pinId, "http://x/i.png", taskId, now) } returns
            MediaDownload(pinId, "http://x/i.png", DownloadStatus.PENDING, null, null, taskId, now, now)

        val result = subject.request(pinId, owner, "http://x/i.png")

        assertEquals(DownloadStatus.PENDING, result.status)
        verify { downloads.upsertPending(pinId, "http://x/i.png", taskId, now) }
    }

    @Test
    fun `Given a valid request, Then it enqueues pin download and upserts a PENDING row atomically`() {
        val taskId = randomUUID()
        every { pins.findPinById(pinId) } returns pin()
        every { enqueue.enqueue(any(), any(), any(), any(), any(), any()) } returns aTask(taskId)
        every { downloads.upsertPending(pinId, "https://x/i.png", taskId, now) } returns
            MediaDownload(pinId, "https://x/i.png", DownloadStatus.PENDING, null, null, taskId, now, now)
        val kindSlot = slot<String>(); val payloadSlot = slot<String>(); val dedupSlot = slot<String?>()
        every {
            enqueue.enqueue(capture(kindSlot), capture(payloadSlot), any(), any(), any(), captureNullable(dedupSlot))
        } returns aTask(taskId)

        val result = subject.request(pinId, owner, "https://x/i.png")

        assertEquals(DownloadStatus.PENDING, result.status)
        assertEquals(PinDownloadTask.KIND, kindSlot.captured)
        assertEquals(pinId.toString(), payloadSlot.captured)
        assertEquals("${PinDownloadTask.KIND}:$pinId", dedupSlot.captured)
        verify { downloads.upsertPending(pinId, "https://x/i.png", taskId, now) }
    }
}
