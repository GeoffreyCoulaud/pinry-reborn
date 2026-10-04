package fr.geoffreyCoulaud.pinryReborn.api.utilities

import java.io.InputStream
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs a command as a child process, destroyed with its descendants on every exit path or past [timeout]; under
 * [maxAddressSpace] in bytes, capped by `prlimit --as` with the thread settings the cap was measured under (ADR 0050).
 */
class ProcessRunner(private val timeout: Duration, private val maxAddressSpace: Long? = null) {
    /** Runs [command] in [directory], calling [onTick] about every tenth of a second; what it throws ends the run. */
    fun run(command: List<String>, directory: Path? = null, onTick: () -> Unit = {}): ProcessOutcome {
        val builder = ProcessBuilder(capped(command)).directory(directory?.toFile())
        if (maxAddressSpace != null) builder.environment() += BOUNDED_THREADS
        val process = builder.start()
        try {
            val output = read(process.inputStream)
            val errors = read(process.errorStream)
            val deadline = System.nanoTime() + timeout.toNanos()
            while (true) {
                val wait = (deadline - System.nanoTime()).coerceIn(0, TICK_NANOS)
                val exited = process.waitFor(wait, TimeUnit.NANOSECONDS)
                onTick()
                if (exited) return exitedBy(deadline, process.exitValue(), output, errors)
                if (System.nanoTime() >= deadline) return ProcessOutcome.TimedOut(timeout)
            }
        } finally {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly().waitFor()
        }
    }

    // An orphaned descendant can hold the pipes past the child's exit, beyond reach of descendants().
    private fun exitedBy(deadline: Long, code: Int, output: FutureTask<String>, errors: FutureTask<String>) =
        try {
            val left = { (deadline - System.nanoTime()).coerceAtLeast(0) }
            val read = { stream: FutureTask<String> -> stream.get(left(), TimeUnit.NANOSECONDS) }
            ProcessOutcome.Exited(code, read(output), read(errors))
        } catch (_: TimeoutException) {
            ProcessOutcome.TimedOut(timeout)
        }

    private fun capped(command: List<String>) =
        if (maxAddressSpace == null) command else listOf("prlimit", "--as=$maxAddressSpace", "--") + command

    private fun read(stream: InputStream) =
        FutureTask { stream.readAllBytes().decodeToString() }.also { Thread.ofVirtual().start(it) }

    private companion object {
        val TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(100)

        // Without them the address space follows the cores, not the pixels: 64 MiB per glibc arena, one per thread.
        val BOUNDED_THREADS = mapOf("MALLOC_ARENA_MAX" to "2", "VIPS_CONCURRENCY" to "2")
    }
}

/** How a [ProcessRunner] run ended; the caller maps each to its own exceptions. */
sealed interface ProcessOutcome {
    data class Exited(val code: Int, val output: String, val errors: String) : ProcessOutcome

    data class TimedOut(val timeout: Duration) : ProcessOutcome
}
