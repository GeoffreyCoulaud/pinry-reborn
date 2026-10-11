package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import jakarta.enterprise.context.ApplicationScoped

/** Enqueues the drain, covering a lost enqueue (ADR 0051, decision 7); the database deletes orphans (ADR 0058). */
@ApplicationScoped
class ReapFingerprints(private val enqueueTask: EnqueueTask) {
    fun reap() {
        MediaFingerprintTask.enqueueOn(enqueueTask)
    }
}
