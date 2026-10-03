package fr.geoffreyCoulaud.pinryReborn.api.worker

import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.UserDataImportRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.UserDataImportTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class UserDataImportTaskHandlerTest {
    private val runner: UserDataImportRunner = mockk(relaxed = true)
    private val config: ImportsConfig = mockk()
    private val handler = UserDataImportTaskHandler(runner, config)

    @Test
    fun `Given the kind the completer enqueues, Then this handler answers it`() {
        // Given, When, Then: a kind nothing enqueues leaves every import PENDING for ever.
        assertEquals(UserDataImportTask.KIND, handler.kind)
    }

    @Test
    fun `Given the configured retry floor, Then this kind carries it to the queue`() {
        // Given: the key exists so five attempts outlast an operator, which only a reader makes true.
        every { config.retryFloor() } returns Duration.ofMinutes(10)

        // When, Then
        assertEquals(Duration.ofMinutes(10), handler.retryFloor)
    }

    @Test
    fun `Given the final attempt, Then the runner is told it is the last one`() {
        // Given
        val importId = UUID.randomUUID()

        // When
        handler.handle(importId.toString(), TaskContext(attempt = 5, maxAttempts = 5))

        // Then
        verify { runner.run(importId, isLastAttempt = true, renewLeaseIfDue = any()) }
    }

    @Test
    fun `Given an earlier attempt, Then the runner is told it is not the last one`() {
        // Given
        val importId = UUID.randomUUID()

        // When
        handler.handle(importId.toString(), TaskContext(attempt = 1, maxAttempts = 5))

        // Then
        verify { runner.run(importId, isLastAttempt = false, renewLeaseIfDue = any()) }
    }

    @Test
    fun `Given a throttled lease heartbeat on the context, Then the runner is handed that heartbeat`() {
        // Given: the runner calls it on every line, so the unthrottled one would write a row per line
        val importId = UUID.randomUUID()
        val heartbeat: () -> Unit = {}
        val context = TaskContext(attempt = 1, maxAttempts = 5)
        context.renewLeaseIfDue = heartbeat

        // When
        handler.handle(importId.toString(), context)

        // Then
        verify { runner.run(importId, isLastAttempt = false, renewLeaseIfDue = heartbeat) }
    }
}
