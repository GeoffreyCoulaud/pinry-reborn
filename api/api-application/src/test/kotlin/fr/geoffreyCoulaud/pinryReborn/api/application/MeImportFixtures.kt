package fr.geoffreyCoulaud.pinryReborn.api.application

import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.worker.ImportsConfig
import io.restassured.RestAssured.given
import io.restassured.response.Response
import jakarta.inject.Inject
import java.io.File
import java.util.UUID
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * The wire path and the seeding the `/api/v1/me/imports` suites share, each running end to end over the real REST
 * surface, archive store, libvips probe and async worker (spec `docs/specs/2026-08-14-user-data-import.md` section 13).
 */
@Suppress("AbstractClassCanBeConcreteClass") // Abstract by intent: the wire path the import suites share.
abstract class MeImportFixtures : IntegrationTest() {
    @Inject lateinit var pinCreator: PinCreator

    @Inject lateinit var boardCreator: BoardCreator

    @Inject lateinit var pinRepository: PinRepositoryInterface

    @Inject lateinit var boardRepository: BoardRepositoryInterface

    @Inject lateinit var tagRepository: TagRepositoryInterface

    @Inject lateinit var mediaRepository: MediaRepositoryInterface

    @Inject lateinit var importRepository: UserDataImportRepositoryInterface

    @Inject lateinit var mediaStore: MediaStore

    @Inject lateinit var importsConfig: ImportsConfig

    @Inject lateinit var objectMapper: ObjectMapper

    // --- The wire path: open, upload, complete, poll ---

    protected fun openImport(auth: AuthenticatedUser): UUID =
        given()
            .authenticatedAs(auth)
            .`when`()
            .post("/api/v1/me/imports")
            .then()
            .statusCode(202)
            .extract()
            .jsonPath()
            .getString("id")
            .let(UUID::fromString)

    protected fun uploadChunk(auth: AuthenticatedUser, importId: UUID, bytes: ByteArray, offset: Long): Response =
        given()
            .authenticatedAs(auth)
            .contentType("application/octet-stream")
            .body(bytes)
            .`when`()
            .put("/api/v1/me/imports/$importId/archive?offset=$offset")

    protected fun completeArchive(auth: AuthenticatedUser, importId: UUID) {
        given()
            .authenticatedAs(auth)
            .`when`()
            .post("/api/v1/me/imports/$importId/archive/complete")
            .then()
            .statusCode(202)
            .body("state", equalTo("PENDING"))
    }

    private fun importState(auth: AuthenticatedUser, importId: UUID): String =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/me/imports/$importId")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("state")

    /** Bounded poll until the row stops moving; the last state observed is what the caller asserts. */
    protected fun pollUntilSettled(auth: AuthenticatedUser, importId: UUID): String {
        var last = "UNKNOWN"
        repeat(POLL_ATTEMPTS) {
            last = importState(auth, importId)
            if (last in TERMINAL_STATES) return last
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return last
    }

    /** The whole wire path in one chunk, for every case that is not about the upload itself. */
    protected fun importArchive(auth: AuthenticatedUser, archive: ByteArray): UUID {
        val importId = openImport(auth)
        uploadChunk(auth, importId, archive, 0).then().statusCode(200)
        completeArchive(auth, importId)
        assertEquals("COMPLETED", pollUntilSettled(auth, importId), "the import should complete within the bound")
        return importId
    }

    protected fun issueKinds(auth: AuthenticatedUser, importId: UUID): List<String> =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/me/imports/$importId/issues?pageSize=$ISSUE_PAGE_SIZE")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("issues.kind", String::class.java)

    protected fun counters(auth: AuthenticatedUser, importId: UUID) =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/me/imports/$importId")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()

    // --- Seeding ---

    protected fun fixture(name: String) = File("src/test/resources/fixtures/$name")

    protected fun uploadMedia(auth: AuthenticatedUser, pinId: UUID, name: String, mediaType: String) {
        given()
            .authenticatedAs(auth)
            .multiPart("file", fixture(name), mediaType)
            .`when`()
            .put("/api/v1/pins/$pinId/media")
            .then()
            .statusCode(201)
    }

    protected fun createPin(
        auth: AuthenticatedUser,
        slug: String,
        tags: List<String> = emptyList(),
        sourceContextUrl: String? = "https://example.test/$slug",
    ) =
        pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = sourceContextUrl?.let(HttpUrl::parse),
            sourceMediaUrl = HttpUrl.parse("https://example.test/$slug.jpg"),
            description = "Pin $slug",
            tags = tags,
        )

    /** The account's active pins. No case here seeds more than a handful, so one page holds them. */
    protected fun activePinsOf(user: User): List<Pin> =
        pinRepository
            .findPinsForUser(
                reader = user,
                cursor = null,
                pageSize = 50,
                sortStrategy = PinSortStrategy.CREATED_AT_ASC,
            )
            .items

    // --- Fixtures shared by several cases ---

    protected fun oneGoodPinArchive(): ByteArray {
        val png = fixture("sample.png").readBytes()
        return ImportArchiveBuilder(objectMapper)
            .manifest(announcedPins = 1)
            .tags("nature")
            .boards()
            .entry("media/only.png", png)
            .pins(
                ImportArchiveBuilder.pinLine(
                    sourceContextUrl = "https://example.test/only",
                    tags = listOf("nature"),
                    mediaPath = "media/only.png",
                    mediaSha256 = ImportArchiveBuilder.sha256(png),
                )
            )
            .bytes()
    }

    protected companion object {
        const val POLL_ATTEMPTS = 50
        const val POLL_INTERVAL_MS = 200L
        const val ISSUE_PAGE_SIZE = 100
        val TERMINAL_STATES = setOf("COMPLETED", "FAILED", "CANCELLED", "ABANDONED")
    }
}
