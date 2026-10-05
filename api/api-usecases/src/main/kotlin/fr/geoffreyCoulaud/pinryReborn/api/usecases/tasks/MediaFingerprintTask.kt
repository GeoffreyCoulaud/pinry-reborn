package fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks

import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.Task

/** Identity and retry budget of the drain that hashes every outdated media (ADR 0051, decision 6). */
object MediaFingerprintTask {
    const val KIND = "media.fingerprint"
    const val MAX_ATTEMPTS = 3

    /** One drain at a time: its one dedup key returns the live drain rather than inserting a second. */
    fun enqueueOn(enqueueTask: EnqueueTask): Task =
        enqueueTask.enqueue(KIND, payload = "", MAX_ATTEMPTS, dedupKey = KIND)
}
