package fr.geoffreyCoulaud.pinryReborn.api.domain.tasks

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NewTaskTest {
    @Test
    fun `Given a dedup key at its bound, Then the task holds it`() {
        // Given
        val key = "k".repeat(NewTask.MAX_DEDUP_KEY_LENGTH)

        // When
        val task = newTask(dedupKey = key)

        // Then
        assertEquals(key, task.dedupKey)
    }

    @Test
    fun `Given a dedup key past its bound, Then the task is refused`() {
        // Given: the bound keeps the key's index entry under PostgreSQL's limit (MigratedSchemaTest)
        val key = "k".repeat(NewTask.MAX_DEDUP_KEY_LENGTH + 1)

        // When, Then
        assertThrows<IllegalArgumentException> { newTask(dedupKey = key) }
    }

    @Test
    fun `Given no dedup key, Then the task holds none`() {
        // When
        val task = newTask(dedupKey = null)

        // Then
        assertEquals(null, task.dedupKey)
    }

    private fun newTask(dedupKey: String?) =
        NewTask(kind = "kind", payload = "", availableAt = Instant.EPOCH, maxAttempts = 1, dedupKey = dedupKey)
}
