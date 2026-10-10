package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import java.util.UUID
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Same isolated, writable data dir as [MediaHostingDataDirTestProfile], plus a tiny `media.max_image_bytes` so a small
 * real image fixture trips the use case's 413 though it is far under `media.max_video_bytes`, left at its default.
 * `quarkus.http.limits.max-body-size` is left at its `application.properties` default too, so each 413 genuinely comes
 * from [fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinMedia], not RESTEasy Reactive rejecting the body outright.
 */
class MediaHostingTinyLimitTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "media.data_dir" to "build/test-media-data/${UUID.randomUUID()}",
            "media.max_image_bytes" to "100",
        )
}

@QuarkusTest
@TestProfile(MediaHostingTinyLimitTestProfile::class)
class MediaHostingOversizeIntegrationTest : IntegrationTest() {

    @Inject lateinit var pinCreator: PinCreator

    @TempDir lateinit var tempDir: Path

    @Test
    fun `Given a tiny media_max_image_bytes limit, Then uploading a bigger image returns 413`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = aPin(auth)

        // When / Then: sample.png (a few hundred bytes) exceeds the 100-byte image limit
        given()
            .authenticatedAs(auth)
            .multiPart("file", File("src/test/resources/fixtures/sample.png"), "image/png")
            .`when`()
            .put("/api/v1/pins/${pin.id}/media")
            .then()
            .statusCode(413)
            .body("code", equalTo("MEDIA_TOO_LARGE"))
    }

    @Test
    fun `Given a file past media_max_video_bytes, Then the use case refuses it in problem+json`() {
        // Given: 51 MiB, past the 50 MiB video bound and under the 64M body limit
        val auth = createAuthenticatedUser()
        val pin = aPin(auth)
        val heavy = tempDir.resolve("heavy.bin").toFile()
        RandomAccessFile(heavy, "rw").use { it.setLength(51L * 1024 * 1024) }

        // When / Then
        given()
            .authenticatedAs(auth)
            .multiPart("file", heavy, "video/mp4")
            .`when`()
            .put("/api/v1/pins/${pin.id}/media")
            .then()
            .statusCode(413)
            .contentType("application/problem+json")
            .body("code", equalTo("MEDIA_TOO_LARGE"))
    }

    private fun aPin(auth: AuthenticatedUser) =
        pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = HttpUrl.parse("https://example.com"),
            sourceMediaUrl = HttpUrl.parse("https://example.com/img.jpg"),
            description = "Oversize test pin",
            tags = emptyList(),
        )
}
