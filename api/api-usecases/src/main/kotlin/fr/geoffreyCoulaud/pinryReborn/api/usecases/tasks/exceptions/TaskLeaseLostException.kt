package fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.exceptions

import java.util.UUID

/**
 * Thrown by a heartbeat the queue refused: the task runs under another attempt's lease or nobody's, and nothing this
 * handler writes can be published. Every handler net rethrows it (`docs/adr/0022`).
 */
class TaskLeaseLostException(val taskId: UUID) : RuntimeException("task $taskId lost its lease")
