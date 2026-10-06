package fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.BackoffPolicy
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.ClaimedTask
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.exceptions.PermanentTaskException
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.exceptions.TaskLeaseLostException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Executes an already-claimed task through its registered [TaskHandler] and settles it (succeeded / rescheduled with
 * backoff / dead / cancelled) via the fenced [TaskQueueInterface] operations. Does not itself claim tasks from the
 * queue; that is the runtime's job.
 */
@ApplicationScoped
class TaskProcessor(
    private val taskQueue: TaskQueueInterface,
    private val registry: TaskHandlerRegistry,
    private val backoffPolicy: BackoffPolicy,
    private val clock: Clock,
) {
    private sealed interface Outcome

    private sealed interface Settled : Outcome

    private data object Success : Settled

    private data class Retryable(val message: String) : Settled

    private data class Permanent(val message: String) : Settled

    /** The lease is another attempt's or nobody's: every mark would be refused by its own guard. */
    private data class Abandoned(val taskId: UUID) : Outcome

    fun execute(claimed: ClaimedTask, leaseDuration: Duration) {
        if (claimed.cancelRequested) {
            taskQueue.markCancelledIfRequested(claimed.id, claimed.leaseId, clock.now())
            return
        }
        val handler = registry.handlerFor(claimed.kind)
        if (handler == null) {
            logger.warn { "task ${claimed.id} has no handler for kind ${claimed.kind}, marking dead" }
            taskQueue.markDead(claimed.id, claimed.leaseId, clock.now(), "no handler for kind ${claimed.kind}")
            return
        }
        val context =
            TaskContext(claimed.attempts, claimed.maxAttempts).apply {
                // The handler pushes its lease back by one full duration; a refusal means the task is no
                // longer RUNNING under this lease, and the handler is told so by exception (docs/adr/0022).
                renewLease = {
                    if (!taskQueue.renewLease(claimed.id, claimed.leaseId, clock.now().plus(leaseDuration))) {
                        throw TaskLeaseLostException(claimed.id)
                    }
                }
                renewLeaseIfDue = throttled(leaseDuration.dividedBy(RENEWALS_PER_LEASE), renewLease)
            }
        when (val outcome = runHandler(handler, claimed.id, claimed.payload, context)) {
            is Abandoned -> logger.warn { "task ${outcome.taskId} lost its lease ${claimed.leaseId}, settling nothing" }
            is Settled -> {
                val now = clock.now()
                if (!taskQueue.markCancelledIfRequested(claimed.id, claimed.leaseId, now)) {
                    settle(claimed, outcome, now, handler.retryFloor)
                }
            }
        }
    }

    /** Runs [renew] once [interval] has passed since the claim or the last renewal. */
    private fun throttled(interval: Duration, renew: () -> Unit): () -> Unit {
        var renewedAt = clock.now()
        return {
            val now = clock.now()
            if (Duration.between(renewedAt, now) >= interval) {
                renew()
                renewedAt = now
            }
        }
    }

    /** [retryFloor] is the handler's, not the queue's: the floor belongs to the kind being settled. */
    private fun settle(claimed: ClaimedTask, outcome: Settled, now: Instant, retryFloor: Duration) {
        when (outcome) {
            is Success -> taskQueue.markSucceeded(claimed.id, claimed.leaseId, now)
            is Permanent -> {
                logger.warn { "task ${claimed.id} failed permanently, marking dead: ${outcome.message}" }
                taskQueue.markDead(claimed.id, claimed.leaseId, now, outcome.message)
            }
            is Retryable ->
                if (claimed.attempts >= claimed.maxAttempts) {
                    logger.warn { "task ${claimed.id} exhausted retries, marking dead: ${outcome.message}" }
                    taskQueue.markDead(claimed.id, claimed.leaseId, now, outcome.message)
                } else {
                    val retryAt = backoffPolicy.nextAttemptAt(claimed.attempts, now, retryFloor)
                    taskQueue.markPendingRetry(claimed.id, claimed.leaseId, retryAt, now, outcome.message)
                }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runHandler(handler: TaskHandler, taskId: UUID, payload: String, context: TaskContext): Outcome =
        try {
            handler.handle(payload, context)
            Success
        } catch (e: TaskLeaseLostException) {
            Abandoned(e.taskId)
        } catch (e: PermanentTaskException) {
            Permanent(e.reason)
        } catch (e: Exception) {
            // Swallowed into a retryable outcome; logged so a repeatedly-failing handler is visible.
            logger.warn(e) { "task $taskId handler threw a retryable failure" }
            Retryable(e.message ?: "transient failure")
        }

    private companion object {
        private const val RENEWALS_PER_LEASE = 3L
        private val logger = KotlinLogging.logger {}
    }
}
