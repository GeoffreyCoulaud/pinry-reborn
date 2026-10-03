package fr.geoffreyCoulaud.pinryReborn.api.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ArchiveLine
import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ArchiveSource
import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ImportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.storage.filesystem.FilesystemZipImportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.worker.ImportsConfig
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** A lease far shorter than a slow task, which no other suite can run under. */
class LeaseRenewalTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "media.data_dir" to "build/test-media-data/${UUID.randomUUID()}",
            "media.download.allow_private_addresses" to "true",
            "tasks.lease_duration" to "PT1S",
        )
}

@QuarkusTest
@TestProfile(LeaseRenewalTestProfile::class)
class LeaseRenewalIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

    @Inject
    lateinit var importsConfig: ImportsConfig

    @Inject
    lateinit var objectMapper: ObjectMapper

    /** A real store whose archives yield each tag line a beat late. */
    private class SlowTagsArchiveStore(private val store: ImportArchiveStore) : ImportArchiveStore by store {
        override fun open(storageKey: String): ArchiveSource = SlowTagsSource(store.open(storageKey))
    }

    private class SlowTagsSource(private val source: ArchiveSource) : ArchiveSource by source {
        override fun <T : Any> readJsonLines(name: String, type: Class<T>, block: (Sequence<ArchiveLine<T>>) -> Unit) =
            source.readJsonLines(name, type) { lines ->
                block(if (name == "tags.jsonl") lines.onEach { Thread.sleep(DRIP_INTERVAL_MS) } else lines)
            }
    }

    @Test
    fun `Given an import whose lines outlast the lease, Then it completes on its first attempt`() {
        // Given: tag lines, since the pin walk renewed on every line before the metadata walks did. A reaped
        // attempt waits out the ten-minute retry floor, so only a renewed lease completes within the poll.
        QuarkusMock.installMockForType(
            SlowTagsArchiveStore(FilesystemZipImportArchiveStore(importsConfig.dataDir(), importsConfig.maxLineBytes())),
            ImportArchiveStore::class.java,
        )
        val auth = createAuthenticatedUser()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .tags(*Array(DRIP_CHUNKS) { "tag-$it" })
                .boards()
                .pins(ImportArchiveBuilder.pinLine(sourceContextUrl = "https://example.test/slow"))
                .bytes()

        // When
        val importId =
            given().authenticatedAs(auth).`when`().post("/api/v1/me/imports")
                .then().statusCode(202).extract().jsonPath().getString("id")
        given().authenticatedAs(auth).contentType("application/octet-stream").body(archive)
            .`when`().put("/api/v1/me/imports/$importId/archive?offset=0").then().statusCode(200)
        given().authenticatedAs(auth).`when`().post("/api/v1/me/imports/$importId/archive/complete")
            .then().statusCode(202)

        // Then
        assertEquals("COMPLETED", pollImportUntilSettled(importId, auth))
    }

    private fun pollImportUntilSettled(importId: String, auth: AuthenticatedUser): String {
        var state = "UNKNOWN"
        repeat(POLL_ATTEMPTS) {
            state =
                given().authenticatedAs(auth).`when`().get("/api/v1/me/imports/$importId")
                    .then().statusCode(200).extract().jsonPath().getString("state")
            if (state != "PENDING" && state != "RUNNING") return state
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return state
    }

    @Test
    fun `Given a fetch slower than the lease, Then it runs once and settles READY`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://example.com",
                sourceMediaUrl = "https://example.com/img.png",
                description = "Lease renewal test pin",
                tags = emptyList(),
            ).id

        // When
        given()
            .authenticatedAs(auth)
            .contentType("application/json")
            .body(mapOf("sourceUrl" to "http://127.0.0.1:$port/slow.png"))
            .`when`().put("/api/v1/pins/$pinId/media")
            .then().statusCode(202)

        // Then
        assertEquals("READY", pollUntilSettled(pinId, auth))
        assertEquals(1, slowFetches.get(), "a reclaimed task fetches the origin a second time")
    }

    private fun pollUntilSettled(pinId: UUID, auth: AuthenticatedUser): String {
        repeat(POLL_ATTEMPTS) {
            val status =
                given()
                    .authenticatedAs(auth)
                    .`when`().get("/api/v1/pins/$pinId/media/status")
                    .then().statusCode(200)
                    .extract().jsonPath().getString("status")
            if (status != "PENDING" && status != "NONE") return status
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("The download of pin $pinId never settled within the poll budget")
    }

    companion object {
        private const val POLL_ATTEMPTS = 100
        private const val POLL_INTERVAL_MS = 100L
        private const val HTTP_OK = 200
        private const val DRIP_CHUNKS = 20
        private const val DRIP_INTERVAL_MS = 200L

        private lateinit var server: HttpServer
        private lateinit var originExecutor: ExecutorService
        private val slowFetches = AtomicInteger()

        @Volatile
        private var port: Int = 0

        @JvmStatic
        @BeforeAll
        fun startOrigin() {
            val pngBytes = Files.readAllBytes(File("src/test/resources/fixtures/sample.png").toPath())
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            originExecutor = Executors.newCachedThreadPool()
            server.executor = originExecutor
            // Drips the body over four lease durations, so only a renewed lease keeps the task from a second claim.
            server.createContext("/slow.png") { exchange ->
                slowFetches.incrementAndGet()
                exchange.use {
                    it.responseHeaders.add("Content-Type", "image/png")
                    it.sendResponseHeaders(HTTP_OK, pngBytes.size.toLong())
                    val chunkSize = pngBytes.size / DRIP_CHUNKS + 1
                    pngBytes.toList().chunked(chunkSize).forEach { chunk ->
                        Thread.sleep(DRIP_INTERVAL_MS)
                        it.responseBody.write(chunk.toByteArray())
                        it.responseBody.flush()
                    }
                }
            }
            server.start()
            port = server.address.port
        }

        @JvmStatic
        @AfterAll
        fun stopOrigin() {
            server.stop(0)
            originExecutor.shutdownNow()
        }
    }
}
