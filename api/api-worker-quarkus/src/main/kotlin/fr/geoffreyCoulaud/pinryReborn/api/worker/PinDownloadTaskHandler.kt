package fr.geoffreyCoulaud.pinryReborn.api.worker

import fr.geoffreyCoulaud.pinryReborn.api.usecases.DownloadPinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.PinDownloadTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskHandler
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PinDownloadTaskHandler(
    private val downloadPinMedia: DownloadPinMedia,
) : TaskHandler {
    override val kind = PinDownloadTask.KIND

    override fun handle(payload: String, context: TaskContext) {
        downloadPinMedia.download(pinId = UUID.fromString(payload), context = context)
    }
}
