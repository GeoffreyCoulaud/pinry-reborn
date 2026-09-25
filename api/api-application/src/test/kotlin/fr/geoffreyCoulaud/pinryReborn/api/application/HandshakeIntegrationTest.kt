package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.ImageFormat
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Numbers no default carries, so a response holding them can only have read the deployment's own
 * configuration.
 */
class HandshakeTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "images.max_file_bytes" to "$MAX_FILE_BYTES",
        "images.max_pixels" to "$MAX_PIXELS",
        "images.renditions.tiny" to "$TINY",
        "images.renditions.small" to "$SMALL",
        "images.renditions.medium" to "$MEDIUM",
        "images.renditions.large" to "$LARGE",
        "imports.max_chunk_bytes" to "$MAX_IMPORT_CHUNK_BYTES",
        "imports.max_archive_bytes" to "$MAX_IMPORT_ARCHIVE_BYTES",
    )

    companion object {
        const val MAX_FILE_BYTES = 1_234_567L
        const val MAX_PIXELS = 7_654_321L
        const val MAX_IMPORT_CHUNK_BYTES = 2_345_678L
        const val MAX_IMPORT_ARCHIVE_BYTES = 98_765_432_109L
        const val TINY = 11
        const val SMALL = 22
        const val MEDIUM = 33
        const val LARGE = 44
    }
}

/**
 * The one route a client calls before it has a session: the contract it negotiates on and the
 * deployment's numbers (`docs/specs/2026-09-10-web-application.md`, section 4.3).
 */
@QuarkusTest
@TestProfile(HandshakeTestProfile::class)
class HandshakeIntegrationTest {

    @Test
    fun `Given no credential, Then the handshake answers`() {
        // Given / When: no Authorization header and no cookie, which every other route refuses
        given()
            .`when`()
            .get(HANDSHAKE_PATH)
            // Then
            .then()
            .statusCode(HTTP_OK)
    }

    @Test
    fun `Given a configured deployment, Then the handshake answers its limits and rendition sizes`() {
        // Given / When
        val body = handshake()

        // Then
        assertEquals(HandshakeTestProfile.MAX_FILE_BYTES, body.getLong("limits.maxFileBytes"))
        assertEquals(HandshakeTestProfile.MAX_PIXELS, body.getLong("limits.maxPixels"))
        assertEquals(HandshakeTestProfile.MAX_IMPORT_CHUNK_BYTES, body.getLong("limits.maxImportChunkBytes"))
        assertEquals(HandshakeTestProfile.MAX_IMPORT_ARCHIVE_BYTES, body.getLong("limits.maxImportArchiveBytes"))
        assertEquals(HandshakeTestProfile.TINY, body.getInt("renditionSizes.tiny"))
        assertEquals(HandshakeTestProfile.SMALL, body.getInt("renditionSizes.small"))
        assertEquals(HandshakeTestProfile.MEDIUM, body.getInt("renditionSizes.medium"))
        assertEquals(HandshakeTestProfile.LARGE, body.getInt("renditionSizes.large"))
    }

    @Test
    fun `Given the formats the storage accepts, Then the handshake answers their media types`() {
        // Given / When / Then
        assertEquals(
            ImageFormat.entries.map { it.mimeType },
            handshake().getList<String>("limits.mediaTypes"),
        )
    }

    @Test
    fun `Given the shipped configuration, Then the handshake answers the contract version it declares`() {
        // Given: the file, not the injected value, so the assertion cannot pass by comparing a
        // number to itself
        val declared = requireNotNull(ProductionProperties[CONTRACT_VERSION_KEY]) {
            "$CONTRACT_VERSION_KEY is not declared, and comparing two absent values proves nothing"
        }

        // When / Then
        assertEquals(declared, handshake().getString("contractVersion"))
    }

    private fun handshake() = given()
        .`when`()
        .get(HANDSHAKE_PATH)
        .then()
        .statusCode(HTTP_OK)
        .extract()
        .jsonPath()

    private companion object {
        const val HANDSHAKE_PATH = "/api/v1/handshake"
        const val CONTRACT_VERSION_KEY = "quarkus.smallrye-openapi.info-version"
        const val HTTP_OK = 200
    }
}
