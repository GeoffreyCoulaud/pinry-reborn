package fr.geoffreyCoulaud.pinryReborn.api.application

import io.quarkus.runtime.configuration.MemorySize
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import java.net.Socket
import java.util.UUID
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(MeImportTestProfile::class)
class MeImportUploadIntegrationTest : ImportIntegrationTest() {
    @ConfigProperty(name = "quarkus.http.limits.max-body-size") lateinit var maxBodySize: MemorySize

    // --- The upload itself ---

    @Test
    fun `Given a replayed offset, Then the refusal names the current length the upload resumes from`() {
        // Given: one small archive cut into three chunks
        val auth = createAuthenticatedUser()
        val archive = oneGoodPinArchive()
        val chunks = archive.toList().chunked((archive.size + 2) / 3).map { it.toByteArray() }
        assertEquals(3, chunks.size)
        val importId = openImport(auth)

        // When: two chunks land, the second is replayed at an offset the upload has passed
        uploadChunk(auth, importId, chunks[0], 0).then().statusCode(200)
        val afterSecond =
            uploadChunk(auth, importId, chunks[1], chunks[0].size.toLong()).then().statusCode(200).extract().jsonPath()
        val replayed =
            uploadChunk(auth, importId, chunks[1], chunks[0].size.toLong())
                .then()
                .statusCode(409)
                .body("code", equalTo("IMPORT_CHUNK_OFFSET_MISMATCH"))
                .extract()
                .jsonPath()

        // Then: the client resumes from the length the refusal reported, read off the problem's own
        // member, since a number parsed out of an English sentence is not a contract.
        val reportedLength = replayed.getLong("currentLength")
        assertEquals(afterSecond.getLong("uploadedBytes"), reportedLength)
        uploadChunk(auth, importId, chunks[2], reportedLength).then().statusCode(200)
        completeArchive(auth, importId)
        assertEquals("COMPLETED", pollUntilSettled(auth, importId))
        assertEquals(1, activePinsOf(auth.user).size)
    }

    @Test
    fun `Given a chunk sent with no offset, Then it lands at the start of the upload`() {
        // Given: the parameter's default is the framework's, so only the wire can pin it
        val auth = createAuthenticatedUser()
        val importId = openImport(auth)
        val bytes = "the first bytes".toByteArray()

        // When: no offset at all
        given()
            .authenticatedAs(auth)
            .contentType("application/octet-stream")
            .body(bytes)
            .`when`()
            .put("/api/v1/me/imports/$importId/archive")
            .then()
            .statusCode(200)
            .body("uploadedBytes", equalTo(bytes.size))

        // Then
        assertEquals(bytes.size.toLong(), importRepository.findById(importId)?.uploadedBytes)
    }

    @Test
    fun `Given a chunk carrying the upload past the maximum, Then it is refused and the length holds`() {
        // Given: the upload filled to imports.max_archive_bytes exactly
        val auth = createAuthenticatedUser()
        val importId = openImport(auth)
        val toTheBound = ByteArray(importsConfig.maxArchiveBytes().toInt())
        uploadChunk(auth, importId, toTheBound, 0).then().statusCode(200)

        // When: one byte more
        val refused = uploadChunk(auth, importId, ByteArray(1), toTheBound.size.toLong())

        // Then: over the wire, since the use-case case for this stubs the store that raises it
        refused
            .then()
            .statusCode(413)
            .contentType("application/problem+json")
            .body("code", equalTo("IMPORT_ARCHIVE_TOO_LARGE"))
        assertEquals(
            toTheBound.size.toLong(),
            importRepository.findById(importId)?.uploadedBytes,
            "a refused chunk leaves the length as it was, so the client resumes rather than restarts",
        )
    }

    @Test
    fun `Given a body declared past the server's limit, Then the 413 carries a problem`() {
        // Given: a raw socket, since a client library refuses to declare a length it does not send
        val declaredLength = maxBodySize.asLongValue() + 1
        val path = "/api/v1/me/imports/${UUID.randomUUID()}/archive"
        val head =
            "PUT $path HTTP/1.1\r\nHost: localhost\r\n" +
                "Content-Type: application/octet-stream\r\nContent-Length: $declaredLength\r\n\r\n"

        // When: the head alone, the refusal reading the declared length and never the body
        val answer =
            Socket("localhost", RestAssured.port).use { socket ->
                socket.getOutputStream().write(head.toByteArray())
                socket.getInputStream().readAllBytes().decodeToString()
            }

        // Then
        val (statusAndHeaders, body) = answer.split("\r\n\r\n", limit = 2)
        assertTrue(statusAndHeaders.startsWith("HTTP/1.1 413"), statusAndHeaders)
        assertTrue(statusAndHeaders.contains("content-type: application/problem+json", ignoreCase = true))
        val problem = objectMapper.readTree(body)
        assertEquals("BODY_TOO_LARGE", problem["code"].asText())
        assertEquals(path, problem["instance"].asText())
    }
}
