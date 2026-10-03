package fr.geoffreyCoulaud.pinryReborn.api.application

import com.sun.net.httpserver.HttpServer
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
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

/** A lease far shorter than the slow origin's body, which no other suite can run under. */
class MediaDownloadLeaseTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "media.data_dir" to "build/test-media-data/${UUID.randomUUID()}",
            "media.download.allow_private_addresses" to "true",
            "tasks.lease_duration" to "PT1S",
        )
}

@QuarkusTest
@TestProfile(MediaDownloadLeaseTestProfile::class)
class MediaDownloadLeaseIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

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
