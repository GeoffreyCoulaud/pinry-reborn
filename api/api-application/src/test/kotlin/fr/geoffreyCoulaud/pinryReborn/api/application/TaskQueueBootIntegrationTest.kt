package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.TaskState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import io.ebean.DB
import io.ebean.datasource.DataSourcePool
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Proves the whole runtime -- real Quarkus boot, real background poller/worker pool
 * ([fr.geoffreyCoulaud.pinryReborn.api.worker.TaskWorkerLifecycle]), real PostgreSQL -- processes a task end to end.
 * Enqueues a task of an unknown kind (no [fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskHandler] is registered
 * for it in this test app), so [fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskProcessor]'s no-handler path
 * settles it straight to DEAD. This exercises claim -> execute -> settle without needing a test-only handler.
 */
@QuarkusTest
class TaskQueueBootIntegrationTest : IntegrationTest() {
    @Inject lateinit var enqueueTask: EnqueueTask

    @Inject lateinit var taskQueue: TaskQueueInterface

    /**
     * The suite declares its own database (`docs/adr/0012`, ADR 0059 decision 3). The write goes through an injected
     * port so the asserted handle is provably the one the application uses.
     */
    @Test
    fun `Given a task written through the injected port, Then the handle that reads it is the suite's database`() {
        // Given: one row, written by the application through its own port
        enqueueTask.enqueue(kind = "no.handler", payload = "{}", maxAttempts = 1)

        // When: the handle under assertion reads the tasks table and names its database
        val database = DB.getDefault()
        val taskCount =
            database.sqlQuery("select count(*) as task_count from tasks").findOne()?.getInteger("task_count")
        val name = database.sqlQuery("select current_database() as name").findOne()?.getString("name")

        // Then: that handle sees the write, in this suite's database and no other
        assertEquals(1, taskCount, "Expected the handle to read the row the port wrote")
        assertEquals("pinry_application", name)
    }

    @Test
    fun `Given the application's database, Then its pool holds one connection`() {
        // When: ebean-test rewrites the address and leaves the pool as the properties declare it
        val maximum = (DB.getDefault().dataSource() as DataSourcePool).status(false).maxSize()

        // Then
        assertEquals(1, maximum)
    }

    @Test
    fun `Given an enqueued task with no handler, Then the runtime settles it to DEAD`() {
        // Given
        val task = enqueueTask.enqueue(kind = "no.handler", payload = "{}", maxAttempts = 1)

        // When: the background poller claims and processes it (no handler -> DEAD)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var state = taskQueue.findById(task.id)?.state
        while (state != TaskState.DEAD && System.nanoTime() < deadline) {
            Thread.sleep(50)
            state = taskQueue.findById(task.id)?.state
        }

        // Then
        assertEquals(TaskState.DEAD, state)
    }
}
