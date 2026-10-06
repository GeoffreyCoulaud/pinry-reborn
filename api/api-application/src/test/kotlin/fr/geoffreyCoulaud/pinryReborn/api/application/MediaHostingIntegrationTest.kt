package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * Isolated, writable `media.data_dir` for the whole class run: a fresh UUID-suffixed directory
 * under the module's `build/`, so these tests never touch the production default
 * (`/var/lib/pinry/media`, not writable in CI) and successive local runs never collide.
 */
class MediaHostingDataDirTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf("media.data_dir" to "build/test-media-data/${UUID.randomUUID()}")
}

/**
 * End-to-end coverage of the canonical-media-hosting flow through the fully wired app: multipart
 * PUT upload/replace, conditional GET (`ETag`/`If-None-Match`), DELETE, and the interaction with
 * pin permanent deletion. This is what validates the spec's flagged risk -- a real multipart PUT,
 * routed by RESTEasy Reactive through `@RestForm`/`FileUpload`, exercised for real against a
 * running app (not a controller unit test).
 */
@QuarkusTest
@TestProfile(MediaHostingDataDirTestProfile::class)
class MediaHostingIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

    @Inject
    lateinit var mediaRepository: MediaRepositoryInterface

    @Inject
    lateinit var mediaConfig: MediaConfig

    private fun fixture(name: String) = File("src/test/resources/fixtures/$name")

    private fun createPinForNewUser(): Pair<AuthenticatedUser, UUID> {
        val auth = createAuthenticatedUser()
        val pin = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "Image hosting test pin",
            tags = emptyList(),
        )
        return auth to pin.id
    }

    @Test
    fun `Given own pin, Then upload returns 201, GET returns 200 with ETag, and If-None-Match returns 304`() {
        // Given
        val (auth, pinId) = createPinForNewUser()

        // When: multipart PUT upload
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)
            .body("url", equalTo("/api/v1/pins/$pinId/media"))

        // Then: GET returns the image with an ETag
        val etag = given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(200)
            .contentType("image/png")
            .header("ETag", matchesPattern("\"[0-9a-f]{64}\""))
            .extract()
            .header("ETag")

        // Then: re-GET with a matching If-None-Match returns 304
        given()
            .authenticatedAs(auth)
            .header("If-None-Match", etag)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(304)
    }

    @Test
    fun `Given an uploaded image, Then a Range answers 206 with that slice, and one past its end 416`() {
        // Given
        val (auth, pinId) = createPinForNewUser()
        val bytes = fixture("sample.png").readBytes()
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)

        // Then: no Range serves the whole original and advertises ranges
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(200)
            .header("Accept-Ranges", "bytes")

        // Then: the first ten bytes
        val slice = given()
            .authenticatedAs(auth)
            .header("Range", "bytes=0-9")
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(206)
            .header("Content-Range", "bytes 0-9/${bytes.size}")
            .extract()
            .asByteArray()
        assertArrayEquals(bytes.copyOfRange(0, 10), slice)

        // Then: a start at the size is past the end
        given()
            .authenticatedAs(auth)
            .header("Range", "bytes=${bytes.size}-")
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(416)
            .header("Content-Range", "bytes */${bytes.size}")
    }

    @Test
    fun `Given an existing image, Then replacing it returns 200 and GET reflects the new bytes and ETag`() {
        // Given
        val (auth, pinId) = createPinForNewUser()
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)
        val originalEtag = given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(200)
            .extract()
            .header("ETag")

        // When: replace with a different image
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("sample.jpg"), "image/jpeg")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(200)
            .body("url", equalTo("/api/v1/pins/$pinId/media"))

        // Then: GET reflects the replaced image
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(200)
            .contentType("image/jpeg")
            .header("ETag", notNullValue())
            .header("ETag", not(originalEtag))
    }

    @Test
    fun `Given a pin owned by someone else, Then uploading an image returns 403`() {
        // Given
        val (_, pinId) = createPinForNewUser()
        val intruder = createAuthenticatedUser()

        // When / Then
        given()
            .authenticatedAs(intruder)
            .multiPart("file", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(403)
    }

    @Test
    fun `Given a non-media file, Then uploading it returns 422`() {
        // Given
        val (auth, pinId) = createPinForNewUser()

        // When / Then
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("not-an-image.txt"), "text/plain")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(422)
    }

    @Test
    fun `Given a multipart upload with no file part, Then it returns 400 VALIDATION_ERROR`() {
        // Given
        val (auth, pinId) = createPinForNewUser()

        // When / Then
        given()
            .authenticatedAs(auth)
            .multiPart("other", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(400)
            .body("code", equalTo("VALIDATION_ERROR"))
    }

    @Test
    fun `Given a pin with no image, Then GET returns 404`() {
        // Given
        val (auth, pinId) = createPinForNewUser()

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(404)
    }

    @Test
    fun `Given an uploaded image, Then DELETE returns 204 and the subsequent GET returns 404`() {
        // Given
        val (auth, pinId) = createPinForNewUser()
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)

        // When
        given()
            .authenticatedAs(auth)
            .`when`().delete("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(204)

        // Then
        given()
            .authenticatedAs(auth)
            .`when`().get("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(404)
    }

    @Test
    fun `Given a pin with an image, Then permanently deleting the pin removes the stored file from disk`() {
        // Given
        val (auth, pinId) = createPinForNewUser()
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture("sample.png"), "image/png")
            .`when`().put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)
        val media = requireNotNull(mediaRepository.findByPinId(pinId)) { "image should exist right after upload" }
        val storedPath: Path = Path.of(mediaConfig.dataDir()).resolve(media.storageKey)
        assertTrue(Files.exists(storedPath), "uploaded image file should exist on disk at $storedPath")

        // When: soft-delete then empty the recycle bin (permanent delete)
        given().authenticatedAs(auth).delete("/api/v1/pins/$pinId").then().statusCode(204)
        given()
            .authenticatedAs(auth)
            .`when`().delete("/api/v1/pins/recycled")
            .then()
            .statusCode(204)

        // Then: the stored file is gone
        assertFalse(Files.exists(storedPath), "image file should be removed from disk after permanent delete")
    }

    @Test
    fun `Given each accepted video, Then the upload answers 201 and the original is served with its codecs`() {
        // Given: each fixture and the container its codecs choose (decision L1)
        val containers = mapOf(
            "h264-aac.mkv" to "video/mp4",
            "h265-hev1-aac.mov" to "video/mp4",
            "vp9-opus.webm" to "video/webm",
            "av1.mp4" to "video/webm",
        )
        for ((name, container) in containers) {
            val (auth, pinId) = createPinForNewUser()

            // When
            given()
                .authenticatedAs(auth)
                .multiPart("file", videoFixture(name), "application/octet-stream")
                .`when`().put("/api/v1/pins/$pinId/media")
                .then()
                .statusCode(201)

            // Then
            given()
                .authenticatedAs(auth)
                .`when`().get("/api/v1/pins/$pinId/media")
                .then()
                .statusCode(200)
                .header("Content-Type", matchesPattern("$container; codecs=\"[^\"]+\""))
        }
    }

    @Test
    fun `Given a video or a format the server refuses, Then the upload answers its refusal code`() {
        // Given: AC-3 audio, 121 seconds, and an AVIF libvips reads and ffprobe finds a single frame in
        val refusals = mapOf(
            "h264-ac3.mkv" to (415 to "MEDIA_CODEC_UNSUPPORTED"),
            "too-long.mkv" to (422 to "MEDIA_TOO_LONG"),
            "still.avif" to (415 to "MEDIA_CODEC_UNSUPPORTED"),
        )
        for ((name, refusal) in refusals) {
            val (auth, pinId) = createPinForNewUser()

            // When / Then
            given()
                .authenticatedAs(auth)
                .multiPart("file", videoFixture(name), "application/octet-stream")
                .`when`().put("/api/v1/pins/$pinId/media")
                .then()
                .statusCode(refusal.first)
                .body("code", equalTo(refusal.second))
        }
    }

    @Test
    fun `Given a still, an animated GIF and a video, Then each stored media carries its frames and duration`() {
        // Given: what each file holds, the video's as ffprobe counts and reads it
        val expected = mapOf(
            fixture("sample.png") to (1 to null),
            fixture("animated.gif") to (3 to null),
            videoFixture("vp9-opus.webm") to (10 to Duration.ofMillis(1_008)),
        )
        for ((file, framesAndDuration) in expected) {
            val (auth, pinId) = createPinForNewUser()

            // When
            given()
                .authenticatedAs(auth)
                .multiPart("file", file, "application/octet-stream")
                .`when`().put("/api/v1/pins/$pinId/media")
                .then()
                .statusCode(201)

            // Then
            val media = requireNotNull(mediaRepository.findByPinId(pinId)) { "${file.name} should be stored" }
            assertEquals(framesAndDuration, media.frames to (media as? Media.Video)?.duration, file.name)
        }
    }

    @Test
    fun `Given videos with and without sound and a GIF, Then each stored media carries its rates and channels`() {
        // Given: each track's packet bits as ffprobe sums them over the file's duration, then the channels
        val expected = mapOf(
            videoFixture("h264-aac-stereo.mp4") to listOf(11_652 * 8 / 1.0, 2.0, 16_347 * 8 / 1.0),
            videoFixture("vp9-opus.webm") to listOf(10_013 * 8 / 1.008, 1.0, 9_276 * 8 / 1.008),
            videoFixture("vp9.webm") to listOf(10_013 * 8 / 1.0, null, null),
            fixture("animated.gif") to listOf(null, null, null),
        )
        for ((file, tracks) in expected) {
            val (auth, pinId) = createPinForNewUser()

            // When
            given()
                .authenticatedAs(auth)
                .multiPart("file", file, "application/octet-stream")
                .`when`().put("/api/v1/pins/$pinId/media")
                .then()
                .statusCode(201)

            // Then: within 1 %
            val media = requireNotNull(mediaRepository.findByPinId(pinId)) { "${file.name} should be stored" }
            val video = media as? Media.Video
            val stored = listOf(video?.videoBitRate, video?.sound?.channels, video?.sound?.bitRate)
            for ((want, got) in tracks.zip(stored)) {
                if (want == null) {
                    assertNull(got, file.name)
                } else {
                    assertEquals(want, requireNotNull(got) { file.name }.toDouble(), want / 100, file.name)
                }
            }
        }
    }

    // The probe's own fixtures, generated once in the module that reads them (its README holds the commands).
    private fun videoFixture(name: String) = File("../api-video-ffmpeg/src/test/resources/fixtures/$name")
}
