package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import jakarta.enterprise.context.ApplicationScoped

/** Asks the fingerprint task to hash every outdated media, covering a lost enqueue (ADR 0051, decision 7). */
@ApplicationScoped
class RequestOutdatedFingerprintRefresh(private val enqueueTask: EnqueueTask) {
    fun request() {
        MediaFingerprintTask.enqueueOn(enqueueTask)
    }
}
