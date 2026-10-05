package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaFrameRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReapFingerprintsTest : BaseTest() {
    private val frames = mockk<MediaFrameRepositoryInterface>()
    private val duplicates = mockk<PinDuplicateRepositoryInterface>()
    private val enqueueTask = mockk<EnqueueTask>(relaxed = true)
    private val reaper = ReapFingerprints(frames, duplicates, enqueueTask)

    @Test
    fun `Given orphaned frames and pairs, Then both are deleted, counted, and the drain is enqueued`() {
        // Given
        every { frames.deleteOrphans() } returns 3
        every { duplicates.deleteOrphans() } returns 2

        // When
        val deleted = reaper.reap()

        // Then
        assertEquals(5, deleted)
        verify { enqueueTask.enqueue(MediaFingerprintTask.KIND, "", any(), any(), any(), MediaFingerprintTask.KIND) }
    }
}
