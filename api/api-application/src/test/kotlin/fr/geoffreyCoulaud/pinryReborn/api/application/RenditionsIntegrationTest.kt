package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Below-the-fixtures rendition sizes so a real downscale happens against the 10x10 fixtures
 * (`sample.png`, `animated.gif`), and an isolated, writable `media.data_dir` per run so these
 * tests never touch the production default and successive local runs never collide.
 */
class RenditionsTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "media.data_dir" to "build/test-media-data/${UUID.randomUUID()}",
        "media.renditions.tiny" to "4",
        "media.renditions.small" to "6",
    )
}

/**
 * End-to-end coverage of rendition serving through the fully wired app with real libvips + real
 * filesystem cache: WebP renditions at the correct shortest side, animated vs flattened output,
 * original-as-is when the requested size is not smaller than the source, a 400 on an unknown
 * size, and cache eviction on both delete and replace. This is where the animated `page-height`
 * correctness (spec
 * section 13) is validated end-to-end against a real 3-frame GIF, by re-probing the response
 * bytes with the real `VipsImageProbe`.
 */
@QuarkusTest
@TestProfile(RenditionsTestProfile::class)
class RenditionsIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

    @Inject
    lateinit var mediaRepository: MediaRepositoryInterface

    @Inject
    lateinit var mediaConfig: MediaConfig

    @Inject
    lateinit var imageProbe: ImageProbe

    private fun fixture(name: String) = File("src/test/resources/fixtures/$name")

    private fun createPinFor(auth: AuthenticatedUser): UUID {
        val pin = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "rendition test",
            tags = emptyList(),
        )
        return pin.id
    }

    private fun upload(
        auth: AuthenticatedUser,
        pinId: UUID,
        fixtureName: String,
        contentType: String,
        expectedStatus: Int = 201, // a first upload creates (201); replacing an existing image is a 200
    ) {
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture(fixtureName), contentType)
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(expectedStatus)
    }

    private fun probeBytes(bytes: ByteArray): ProbeResult {
        val tmp = Files.createTempFile("resp-", ".bin")
        Files.write(tmp, bytes)
        return try {
            imageProbe.probe(StagedFile(tmp.toString(), 0, ""), maxPixels = 1_000_000)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    @Test
    fun `Given a 10px image and size=tiny (4), Then GET returns a 4px WebP`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "sample.png", "image/png")

        // When
        val bytes = given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=tiny")
            .then()
            .statusCode(200)
            .contentType("image/webp")
            .extract()
            .asByteArray()

        // Then
        val probe = probeBytes(bytes)
        assertEquals(MediaFormat.WEBP, probe.format)
        assertEquals(4, minOf(probe.width, probe.height))
    }

    @Test
    fun `Given no size, Then GET returns the original bytes`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "sample.png", "image/png")

        // When
        val bytes = given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(200)
            .contentType("image/png")
            .extract()
            .asByteArray()

        // Then
        assertArrayEquals(Files.readAllBytes(fixture("sample.png").toPath()), bytes)
    }

    @Test
    fun `Given size=large (960) larger than the image, Then GET serves the original as-is`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "sample.png", "image/png")

        // When / Then: never upscaled, original format
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=large")
            .then()
            .statusCode(200)
            .contentType("image/png")
    }

    @Test
    fun `Given an unknown size, Then GET returns 400`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "sample.png", "image/png")

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=huge")
            .then()
            .statusCode(400)
    }

    @Test
    fun `Given an animated GIF and animated=false, Then the rendition is a static WebP`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "animated.gif", "image/gif")

        // When
        val bytes = given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=tiny&animated=false")
            .then()
            .statusCode(200)
            .contentType("image/webp")
            .extract()
            .asByteArray()

        // Then
        assertFalse(probeBytes(bytes).animated)
    }

    @Test
    fun `Given an animated GIF and the default (animated), Then the rendition keeps the animation`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "animated.gif", "image/gif")

        // When
        val bytes = given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=tiny")
            .then()
            .statusCode(200)
            .contentType("image/webp")
            .extract()
            .asByteArray()

        // Then
        assertTrue(probeBytes(bytes).animated)
    }

    @Test
    fun `Given a video and size=small (6), Then GET returns its poster as a still WebP, cached for the next GET`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        uploadVideo(auth, pinId)
        val mediaId = requireNotNull(mediaRepository.findByPinId(pinId)).id

        // When
        val bytes = getWebp(auth, pinId, "size=small")

        // Then
        val probe = probeBytes(bytes)
        assertFalse(probe.animated)
        assertEquals(6, minOf(probe.width, probe.height))
        val cached = Path.of(mediaConfig.dataDir()).resolve("cache/$mediaId/v2-6-s.webp")
        val firstWrite = Files.getLastModifiedTime(cached)
        assertArrayEquals(bytes, getWebp(auth, pinId, "size=small"))
        assertEquals(firstWrite, Files.getLastModifiedTime(cached), "the second GET should be a cache hit")
    }

    @Test
    fun `Given a video and animated=true, Then GET returns an animated WebP cached apart from the still`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        uploadVideo(auth, pinId)
        val mediaId = requireNotNull(mediaRepository.findByPinId(pinId)).id
        getWebp(auth, pinId, "size=small")

        // When
        val bytes = getWebp(auth, pinId, "size=small&animated=true")

        // Then
        assertTrue(probeBytes(bytes).animated)
        val cache = Path.of(mediaConfig.dataDir()).resolve("cache/$mediaId")
        assertTrue(Files.exists(cache.resolve("v2-6-s.webp")) && Files.exists(cache.resolve("v2-6-a.webp")))
    }

    @Test
    fun `Given a video smaller than size=large (960), Then GET still returns a WebP`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        uploadVideo(auth, pinId)

        // When
        val probe = probeBytes(getWebp(auth, pinId, "size=large"))

        // Then: the 160x120 fixture at its own shortest side, never upscaled
        assertEquals(120, minOf(probe.width, probe.height))
    }

    private fun uploadVideo(auth: AuthenticatedUser, pinId: UUID) {
        given()
            .authenticatedAs(auth)
            .multiPart("file", File("../api-video-ffmpeg/src/test/resources/fixtures/h264-aac.mkv"), "video/mp4")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)
    }

    private fun getWebp(auth: AuthenticatedUser, pinId: UUID, query: String): ByteArray =
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?$query")
            .then()
            .statusCode(200)
            .contentType("image/webp")
            .extract()
            .asByteArray()

    @Test
    fun `Given a cached rendition, Then deleting the image evicts the cache subtree`() {
        // Given
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "sample.png", "image/png")
        val mediaId = requireNotNull(mediaRepository.findByPinId(pinId)).id

        // When: generate + cache a rendition
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=tiny")
            .then()
            .statusCode(200)
        val cacheDir: Path = Path.of(mediaConfig.dataDir()).resolve("cache/$mediaId")
        assertTrue(Files.exists(cacheDir), "rendition cache subtree should exist after first GET")

        // When: delete the image
        given()
            .authenticatedAs(auth)
            .`when`().delete("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(204)

        // Then: the cache subtree is gone
        assertFalse(Files.exists(cacheDir), "rendition cache subtree should be evicted on delete")
    }

    @Test
    fun `Given a cached rendition, Then replacing the image evicts it and a second GET regenerates`() {
        // Given: a pin whose first rendition has been generated and cached
        val auth = createAuthenticatedUser()
        val pinId = createPinFor(auth)
        upload(auth, pinId, "sample.png", "image/png")
        val oldMediaId = requireNotNull(mediaRepository.findByPinId(pinId)).id
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=tiny")
            .then()
            .statusCode(200)
        val oldCacheDir: Path = Path.of(mediaConfig.dataDir()).resolve("cache/$oldMediaId")
        assertTrue(Files.exists(oldCacheDir), "rendition cache subtree should exist after the first GET")

        // When: the canonical image is replaced (mode A)
        upload(auth, pinId, "animated.gif", "image/gif", expectedStatus = 200)

        // Then: the replaced image's cache subtree is evicted
        assertFalse(Files.exists(oldCacheDir), "rendition cache subtree should be evicted on replace")

        // Then: a second GET regenerates a rendition under the new image id
        val newMediaId = requireNotNull(mediaRepository.findByPinId(pinId)).id
        assertNotEquals(oldMediaId, newMediaId, "replacing should mint a new canonical image")
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media?size=tiny")
            .then()
            .statusCode(200)
            .contentType("image/webp")
        assertTrue(
            Files.exists(Path.of(mediaConfig.dataDir()).resolve("cache/$newMediaId")),
            "the rendition should be regenerated under the new image id",
        )
    }
}
