package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.TaskState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.UserDataImportTask
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.io.path.exists
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(MeImportTestProfile::class)
class MeImportCancellationIntegrationTest : ImportIntegrationTest() {
    @Inject lateinit var taskQueue: TaskQueueInterface

    @Inject lateinit var enqueueTask: EnqueueTask

    // --- Cancellation, and the wire's error format ---

    @Test
    fun `Given an import still awaiting its archive, Then cancelling it drops the partial upload`() {
        // Given: one chunk on disk under the upload path, and no task, which this phase never has
        val auth = createAuthenticatedUser()
        val importId = openImport(auth)
        uploadChunk(auth, importId, oneGoodPinArchive(), 0).then().statusCode(200)
        val uploadPath = Path.of(importsConfig.dataDir()).resolve("tmp/import-$importId.part")
        assertTrue(uploadPath.exists(), "the chunk is what the cancellation has to reclaim")

        // When
        given().authenticatedAs(auth).`when`().delete("/api/v1/me/imports/$importId").then().statusCode(204)

        // Then
        val cancelled = requireNotNull(importRepository.findById(importId))
        assertEquals(UserDataImportState.CANCELLED, cancelled.state)
        assertNull(cancelled.taskId)
        assertEquals(0, taskQueue.countByState(TaskState.PENDING), "nothing was queued to cancel")
        assertFalse(uploadPath.exists(), "the partial upload of a cancelled import is reclaimed")
    }

    @Test
    fun `Given a PENDING import, Then cancelling it cancels the task and reclaims the archive`() {
        // Given: the task is enqueued far enough out that the real worker cannot claim it first, the
        // only deterministic way to observe a PENDING row (spec section 13.6).
        val auth = createAuthenticatedUser()
        val importId = openImport(auth)
        val storageKey = "imports/$importId.zip"
        val archivePath = Path.of(importsConfig.dataDir()).resolve(storageKey)
        Files.createDirectories(archivePath.parent)
        Files.write(archivePath, oneGoodPinArchive())
        val task =
            enqueueTask.enqueue(
                kind = UserDataImportTask.KIND,
                payload = importId.toString(),
                maxAttempts = UserDataImportTask.MAX_ATTEMPTS,
                delay = Duration.ofHours(1),
            )
        val opened = requireNotNull(importRepository.findById(importId))
        importRepository.save(
            opened.copy(state = UserDataImportState.PENDING, taskId = task.id, storageKey = storageKey)
        )

        // When
        given().authenticatedAs(auth).`when`().delete("/api/v1/me/imports/$importId").then().statusCode(204)

        // Then
        assertEquals(UserDataImportState.CANCELLED, requireNotNull(importRepository.findById(importId)).state)
        assertEquals(TaskState.CANCELLED, requireNotNull(taskQueue.findById(task.id)).state)
        assertFalse(archivePath.exists(), "the archive of a cancelled import is reclaimed")
    }

    @Test
    fun `Given an unknown import id, Then the refusal is problem json carrying its code`() {
        // Given
        val auth = createAuthenticatedUser()

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/me/imports/${UUID.randomUUID()}")
            .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("code", equalTo("IMPORT_DOES_NOT_EXIST"))
            .body("status", equalTo(404))
    }
}
