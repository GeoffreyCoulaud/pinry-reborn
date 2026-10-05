package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaFrameRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import jakarta.enterprise.context.ApplicationScoped

/** Enqueues the drain, covering a lost enqueue, and deletes what a gone media or pin left (ADR 0051, decision 7). */
@ApplicationScoped
class ReapFingerprints(
    private val frameRepository: MediaFrameRepositoryInterface,
    private val duplicateRepository: PinDuplicateRepositoryInterface,
    private val enqueueTask: EnqueueTask,
) {
    /** Returns the rows deleted. */
    fun reap(): Int {
        MediaFingerprintTask.enqueueOn(enqueueTask)
        return frameRepository.deleteOrphans() + duplicateRepository.deleteOrphans()
    }
}
