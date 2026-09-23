package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.ImagesConfig
import fr.geoffreyCoulaud.pinryReborn.api.worker.ImportsConfig
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.configuration.MemorySize
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Refuses the boot when an upload's published limit is not strictly under `quarkus.http.limits.max-body-size`,
 * which would cut an upload the contract allows. Here because the two keys live in two modules.
 */
@ApplicationScoped
class BodyLimitCheck(
    private val imagesConfig: ImagesConfig,
    private val importsConfig: ImportsConfig,
    @param:ConfigProperty(name = "quarkus.http.limits.max-body-size") private val maxBodySize: MemorySize,
) {
    fun onStart(
        @Observes ignored: StartupEvent,
    ) = verify(imagesConfig.maxFileBytes(), importsConfig.maxChunkBytes(), maxBodySize.asLongValue())

    companion object {
        fun verify(maxFileBytes: Long, maxChunkBytes: Long, maxBodyBytes: Long) {
            requireUnder("images.max_file_bytes", maxFileBytes, maxBodyBytes)
            requireUnder("imports.max_chunk_bytes", maxChunkBytes, maxBodyBytes)
        }

        private fun requireUnder(key: String, bytes: Long, maxBodyBytes: Long) =
            check(bytes < maxBodyBytes) {
                "$key ($bytes) must be strictly under quarkus.http.limits.max-body-size ($maxBodyBytes)"
            }
    }
}
