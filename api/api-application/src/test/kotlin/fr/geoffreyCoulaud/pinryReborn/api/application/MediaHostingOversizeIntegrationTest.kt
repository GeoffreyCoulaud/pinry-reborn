package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

/**
 * Same isolated, writable data dir as [MediaHostingDataDirTestProfile], plus a tiny
 * `media.max_file_bytes` so a small real image fixture trips the use case's 413 without an
 * impractical 30 MiB+ upload fixture. `quarkus.http.limits.max-body-size` (the framework
 * backstop) is left at its `application.properties` default (32M) so the 413 genuinely comes
 * from [fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinMedia] (the app-level limit), not
 * RESTEasy Reactive rejecting the body outright.
 */
class MediaHostingTinyLimitTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "media.data_dir" to "build/test-media-data/${UUID.randomUUID()}",
        "media.max_file_bytes" to "100",
    )
}

@QuarkusTest
@TestProfile(MediaHostingTinyLimitTestProfile::class)
class MediaHostingOversizeIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

    @Test
    fun `Given a tiny media_max_file_bytes limit, Then uploading a bigger image returns 413`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "Oversize test pin",
            tags = emptyList(),
        )

        // When / Then: sample.png (a few hundred bytes) exceeds the 100-byte test limit
        given()
            .authenticatedAs(auth)
            .multiPart("file", File("src/test/resources/fixtures/sample.png"), "image/png")
            .`when`().put("/api/v1/pins/${pin.id}/media")
            .then()
            .statusCode(413)
    }
}
