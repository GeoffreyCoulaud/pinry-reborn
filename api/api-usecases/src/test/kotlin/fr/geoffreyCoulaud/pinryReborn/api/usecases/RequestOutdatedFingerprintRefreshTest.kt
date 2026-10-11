package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class RequestOutdatedFingerprintRefreshTest : BaseTest() {
    private val enqueueTask = mockk<EnqueueTask>(relaxed = true)
    private val useCase = RequestOutdatedFingerprintRefresh(enqueueTask)

    @Test
    fun `Given an outdated fingerprint refresh request, Then the fingerprint task is enqueued`() {
        // When
        useCase.request()

        // Then
        verify { enqueueTask.enqueue(MediaFingerprintTask.KIND, "", any(), any(), any(), MediaFingerprintTask.KIND) }
    }
}
