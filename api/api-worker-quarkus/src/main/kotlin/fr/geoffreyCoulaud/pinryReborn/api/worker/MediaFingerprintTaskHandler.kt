package fr.geoffreyCoulaud.pinryReborn.api.worker

import fr.geoffreyCoulaud.pinryReborn.api.usecases.FingerprintMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskHandler
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MediaFingerprintTaskHandler(private val fingerprintMedia: FingerprintMedia) : TaskHandler {
    override val kind = MediaFingerprintTask.KIND

    override fun handle(payload: String, context: TaskContext) = fingerprintMedia.drain(context.renewLease)
}
