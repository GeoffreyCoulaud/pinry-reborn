package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ReapFingerprintsTest : BaseTest() {
    private val enqueueTask = mockk<EnqueueTask>(relaxed = true)
    private val reaper = ReapFingerprints(enqueueTask)

    @Test
    fun `Given a reap, Then the drain is enqueued`() {
        // When
        reaper.reap()

        // Then
        verify { enqueueTask.enqueue(MediaFingerprintTask.KIND, "", any(), any(), any(), MediaFingerprintTask.KIND) }
    }
}
