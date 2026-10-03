package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaDownloadConfig
import fr.geoffreyCoulaud.pinryReborn.api.worker.GarbageCollectionConfig
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.time.Duration

/**
 * Refuses the boot when `garbage-collection.orphan_grace` is not longer than `media.download.extraction_timeout`,
 * which would let the orphan sweep take a running extraction's file. Here because the two keys live in two modules.
 */
@ApplicationScoped
class OrphanGraceCheck(
    private val garbageCollectionConfig: GarbageCollectionConfig,
    private val mediaDownloadConfig: MediaDownloadConfig,
) {
    fun onStart(
        @Observes ignored: StartupEvent,
    ) = verify(garbageCollectionConfig.orphanGrace(), mediaDownloadConfig.extractionTimeout())

    companion object {
        fun verify(orphanGrace: Duration, extractionTimeout: Duration) =
            check(orphanGrace > extractionTimeout) {
                "garbage-collection.orphan_grace ($orphanGrace) must be longer than " +
                    "media.download.extraction_timeout ($extractionTimeout)"
            }
    }
}
