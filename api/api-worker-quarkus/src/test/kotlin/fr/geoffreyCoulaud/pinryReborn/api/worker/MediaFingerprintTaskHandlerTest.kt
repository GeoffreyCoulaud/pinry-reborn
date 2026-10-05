package fr.geoffreyCoulaud.pinryReborn.api.worker

import fr.geoffreyCoulaud.pinryReborn.api.usecases.FingerprintMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaFingerprintTaskHandlerTest {
    private val fingerprintMedia: FingerprintMedia = mockk(relaxed = true)
    private val handler = MediaFingerprintTaskHandler(fingerprintMedia)

    @Test
    fun `Given the handler, Then its kind is the media fingerprint drain`() {
        assertEquals(MediaFingerprintTask.KIND, handler.kind)
    }

    @Test
    fun `Given a task, Then the drain renews the task's own lease`() {
        // Given
        val context = TaskContext(1, MediaFingerprintTask.MAX_ATTEMPTS)
        val renewLease = {}
        context.renewLease = renewLease

        // When
        handler.handle("", context)

        // Then
        verify { fingerprintMedia.drain(renewLease) }
    }
}
