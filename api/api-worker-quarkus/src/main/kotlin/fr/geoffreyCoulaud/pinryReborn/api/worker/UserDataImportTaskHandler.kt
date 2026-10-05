package fr.geoffreyCoulaud.pinryReborn.api.worker

import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.UserDataImportRunner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskHandler
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.UserDataImportTask
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.util.UUID

/** The `account.import` kind, delegated whole: every branch it triggers lives in the runner. */
@ApplicationScoped
class UserDataImportTaskHandler(
    private val runner: UserDataImportRunner,
    private val config: ImportsConfig,
    private val enqueueTask: EnqueueTask,
) : TaskHandler {
    override val kind = UserDataImportTask.KIND

    /** Not stamped at enqueue: the floor is read from configuration when the task settles. */
    override val retryFloor: Duration get() = config.retryFloor()

    // Once per run rather than per imported media, the drain reading every outdated media anyway.
    override fun handle(payload: String, context: TaskContext) {
        runner.run(
            UUID.fromString(payload),
            isLastAttempt = context.attempt >= context.maxAttempts,
            renewLeaseIfDue = context.renewLeaseIfDue,
        )
        MediaFingerprintTask.enqueueOn(enqueueTask)
    }
}
