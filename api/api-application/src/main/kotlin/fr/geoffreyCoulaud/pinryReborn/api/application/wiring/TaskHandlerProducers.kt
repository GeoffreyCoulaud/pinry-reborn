package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.usecases.DownloadPinMedia
import fr.geoffreyCoulaud.pinryReborn.api.worker.PinDownloadTaskHandler
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

/**
 * CDI wiring for [PinDownloadTaskHandler], hosted in the composition root because it needs the
 * `media.*` limits from [MediaConfig] (owned by the presentation layer) which the worker module
 * must not depend on. The produced bean is collected by the worker's `TaskHandlerRegistry` via
 * `Instance<TaskHandler>`. Companion to [MediaAdapterProducers].
 */
@ApplicationScoped
class TaskHandlerProducers {
    @Produces
    @ApplicationScoped
    fun pinDownloadTaskHandler(
        downloadPinMedia: DownloadPinMedia,
        mediaConfig: MediaConfig,
    ): PinDownloadTaskHandler =
        PinDownloadTaskHandler(downloadPinMedia, mediaConfig.maxFileBytes(), mediaConfig.maxPixels())
}
