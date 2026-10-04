package fr.geoffreyCoulaud.pinryReborn.api.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Runs real commands: `sh`, `sleep`, `pwd` and `python3`. */
class ProcessRunnerTest {
    @TempDir
    lateinit var directory: Path

    private val runner = ProcessRunner(Duration.ofSeconds(60))

    private fun python(statement: String) = listOf("python3", "-c", statement)

    @Test
    fun `Given a command past its timeout, Then it is destroyed and reported timed out`() {
        // Given
        val impatient = ProcessRunner(Duration.ofMillis(200))
        // When
        val outcome = impatient.run(listOf("sleep", "30"))
        // Then
        assertEquals(ProcessOutcome.TimedOut(Duration.ofMillis(200)), outcome)
        assertEquals(0, ProcessHandle.current().children().count())
    }

    @Test
    fun `Given a command that exits non-zero, Then its code and its standard error are reported`() {
        val outcome = runner.run(listOf("sh", "-c", "echo refused >&2; exit 3"))
        assertEquals(ProcessOutcome.Exited(code = 3, output = "", errors = "refused\n"), outcome)
    }

    @Test
    fun `Given a tick callback that throws, Then the run ends with its exception and the command is destroyed`() {
        assertThrows(IllegalStateException::class.java) {
            runner.run(listOf("sleep", "30")) { throw IllegalStateException("stopped") }
        }
        assertEquals(0, ProcessHandle.current().children().count())
    }

    @Test
    fun `Given a command whose own child outlives the timeout, Then that child is destroyed with it`() {
        // Given: a shell waiting on a sleep, which writes the sleep's process id
        val script = "sleep 30 & echo \$! > child; wait"
        // When
        ProcessRunner(Duration.ofSeconds(1)).run(listOf("sh", "-c", script), directory)
        // Then
        val child = ProcessHandle.of(Files.readString(directory.resolve("child")).trim().toLong())
        child.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
        assertFalse(child.map(ProcessHandle::isAlive).orElse(false))
    }

    @Test
    fun `Given a working directory, Then the command runs in it and its standard output is returned`() {
        val outcome = runner.run(listOf("pwd"), directory)
        assertEquals(ProcessOutcome.Exited(code = 0, output = "${directory.toRealPath()}\n", errors = ""), outcome)
    }

    @Test
    fun `Given an address-space cap, Then python3 allocating past it fails and under it succeeds`() {
        // Given
        val capped = ProcessRunner(Duration.ofSeconds(60), maxAddressSpace = 256L * MEBIBYTE)
        // When
        val past = capped.run(python("bytearray(512 * 1024 * 1024)"))
        val under = capped.run(python("bytearray(64 * 1024 * 1024)"))
        // Then
        assertEquals(1, (past as ProcessOutcome.Exited).code, past.errors)
        assertEquals(0, (under as ProcessOutcome.Exited).code, under.errors)
    }

    @Test
    fun `Given an address-space cap, Then the child bounds glibc's arenas and vips' threads to two`() {
        val capped = ProcessRunner(Duration.ofSeconds(60), maxAddressSpace = 256L * MEBIBYTE)
        val outcome = capped.run(listOf("sh", "-c", "echo \$MALLOC_ARENA_MAX \$VIPS_CONCURRENCY"))
        assertEquals("2 2\n", (outcome as ProcessOutcome.Exited).output)
    }

    @Test
    fun `Given no address-space cap, Then the child's arenas and vips' threads are left to their defaults`() {
        val outcome = runner.run(listOf("sh", "-c", "echo \${MALLOC_ARENA_MAX:-unset} \${VIPS_CONCURRENCY:-unset}"))
        assertEquals("unset unset\n", (outcome as ProcessOutcome.Exited).output)
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024
    }
}
