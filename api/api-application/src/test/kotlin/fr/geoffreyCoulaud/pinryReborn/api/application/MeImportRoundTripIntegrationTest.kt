package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonCreator
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(MeImportTestProfile::class)
class MeImportRoundTripIntegrationTest : MeImportFixtures() {
    @Inject lateinit var personCreator: PersonCreator

    @Inject lateinit var remoteCollectionRepository: RemoteCollectionRepositoryInterface

    // --- Seeding, and the real export the round trip pours back in ---

    private fun stepUp(password: String) = "password " + Base64.getUrlEncoder().encodeToString(password.toByteArray())

    /**
     * Two active pins, one recycled pin naming no page, two boards (one recycled) each holding a pin and a collection,
     * two tags, and a fourth pin sharing a medium (spec section 13.1). The first one credits its people.
     */
    private fun seedRoundTripContent(auth: AuthenticatedUser) {
        val alpha = createPin(auth, ALPHA, tags = listOf("nature", "travel"))
        val beta = createPin(auth, BETA, tags = listOf("nature"))
        val gamma = createPin(auth, GAMMA, sourceContextUrl = null)
        val delta = createPin(auth, DELTA)
        uploadMedia(auth, alpha.id, "sample.png", "image/png")
        uploadMedia(auth, beta.id, "sample.jpg", "image/jpeg")
        uploadMedia(auth, gamma.id, "animated.gif", "image/gif")
        // Beta's medium, not alpha's: the pair collapses into whichever line the archive lists first, so
        // the pin carrying the recycled membership must not be one of the two.
        uploadMedia(auth, delta.id, "sample.jpg", "image/jpeg")
        val activeBoard = boardCreator.create(auth.user, "Active board", "kept")
        val recycledBoard = boardCreator.create(auth.user, "Recycled board", "recycled")
        val credited =
            alpha.copy(
                publisher = personCreator.findOrCreate("Studio", listOf("https://studio.example"), auth.user),
                creators =
                    listOf(
                        personCreator.findOrCreate("Ada", listOf("https://b.example", "https://a.example"), auth.user),
                        personCreator.findOrCreate("Grace", emptyList(), auth.user),
                    ),
                publishedAt = PUBLISHED_AT,
            )
        replacePin(auth, credited, boardIds = listOf(activeBoard.id, recycledBoard.id)).statusCode(200)
        linkCollection(activeBoard, "https://remote.example/active")
        linkCollection(recycledBoard, "https://remote.example/recycled")
        given().authenticatedAs(auth).`when`().delete("/api/v1/boards/${recycledBoard.id}").then().statusCode(204)
        given().authenticatedAs(auth).`when`().delete("/api/v1/pins/${gamma.id}").then().statusCode(204)
    }

    private fun linkCollection(board: Board, url: String) {
        remoteCollectionRepository.saveRemoteCollection(
            RemoteCollection(
                id = UUID.randomUUID(),
                author = board.author,
                url = url,
                name = "Collection of ${board.name}",
                board = board,
                createdAt = board.createdAt,
            )
        )
    }

    /** A real export, built by the real worker and downloaded over the wire. */
    private fun exportArchiveOf(auth: AuthenticatedUser, password: String): ByteArray {
        val exportId =
            given()
                .authenticatedAs(auth)
                .header("X-Reauthentication", stepUp(password))
                .`when`()
                .post("/api/v1/me/exports")
                .then()
                .statusCode(202)
                .extract()
                .jsonPath()
                .getString("id")
        repeat(POLL_ATTEMPTS) {
            val state =
                given()
                    .authenticatedAs(auth)
                    .`when`()
                    .get("/api/v1/me/exports/$exportId")
                    .then()
                    .extract()
                    .jsonPath()
                    .getString("state")
            if (state == "READY") return downloadExport(auth, exportId)
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("the export never reached READY")
    }

    private fun downloadExport(auth: AuthenticatedUser, exportId: String): ByteArray =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/me/exports/$exportId/download")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

    // --- What an account holds, in a shape two accounts can be compared on ---

    private data class PinFacts(
        val id: UUID,
        val mediaId: UUID,
        val description: String,
        val sourceMediaUrl: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val deletedAt: Instant?,
        val tagNames: Set<String>,
        val boardNames: Set<String>,
        val mediaBytes: ByteArray,
        val publisher: PersonFacts?,
        val creators: Set<PersonFacts>,
        val publishedAt: Instant?,
    )

    private data class PersonFacts(val name: String, val urls: List<String>)

    private data class AccountFacts(
        val pins: Map<String?, PinFacts>,
        val boards: Map<String, Instant?>,
        val tags: Set<String>,
        val collections: Set<CollectionFacts>,
    )

    private data class CollectionFacts(val url: String, val name: String, val boardName: String)

    private fun factsOf(pin: Pin): PinFacts {
        val media = requireNotNull(mediaRepository.findByPinId(pin.id)) { "pin ${pin.id} carries no image" }
        return PinFacts(
            id = pin.id,
            mediaId = media.id,
            description = pin.description,
            sourceMediaUrl = pin.sourceMediaUrl,
            createdAt = pin.createdAt,
            updatedAt = pin.updatedAt,
            deletedAt = pin.softDeletedAt,
            tagNames = pin.tags.map { it.name }.toSet(),
            // Including the recycled ones, which is the membership the round trip is really about.
            boardNames = pinRepository.findBoardsForPinIncludingRecycled(pin.id).map { it.name }.toSet(),
            mediaBytes = mediaStore.openStream(media.storageKey).use { it.readBytes() },
            publisher = pin.publisher?.let { factsOf(it) },
            creators = pin.creators.map { factsOf(it) }.toSet(),
            publishedAt = pin.publishedAt,
        )
    }

    private fun factsOf(person: Person): PersonFacts = PersonFacts(person.name, person.urls)

    private fun factsOf(user: User): AccountFacts {
        val pins = activePinsOf(user) + pinRepository.findAllSoftDeletedPinsForUser(user)
        val boards = boardRepository.findActiveBoardsForUser(user) + boardRepository.findRecycledBoardsForUser(user)
        return AccountFacts(
            pins = pins.associate { it.sourceContextUrl to factsOf(it) },
            boards = boards.associate { it.name to it.softDeletedAt },
            tags = tagRepository.findAllTagsForUser(user).map { it.name }.toSet(),
            collections =
                remoteCollectionRepository
                    .findAllRemoteCollectionsForUser(user)
                    .map { CollectionFacts(it.url, it.name, it.board.name) }
                    .toSet(),
        )
    }

    /** Spec section 13.1's enumeration: the word "equivalent" is not an assertion, so this is the list. */
    private fun assertSamePin(source: PinFacts, imported: PinFacts) {
        assertEquals(source.description, imported.description)
        assertEquals(source.sourceMediaUrl, imported.sourceMediaUrl)
        assertEquals(source.createdAt, imported.createdAt)
        assertEquals(source.updatedAt, imported.updatedAt)
        assertEquals(source.deletedAt, imported.deletedAt)
        assertEquals(source.tagNames, imported.tagNames)
        assertEquals(source.boardNames, imported.boardNames)
        assertEquals(source.publisher, imported.publisher)
        assertEquals(source.creators, imported.creators)
        assertEquals(source.publishedAt, imported.publishedAt)
        assertArrayEquals(source.mediaBytes, imported.mediaBytes, "the medium should survive byte for byte")
        assertNotEquals(source.id, imported.id, "the copy must be a new row, never the same identifier")
        assertNotEquals(source.mediaId, imported.mediaId)
    }

    // --- The round trip ---

    @Test
    fun `Given a real archive poured into a second account, Then every field survives and no identifier does`() {
        // Given: the destination account first, since the import clamps every restored instant to that
        // account's creation, and one created later would flatten all of them onto its own birth.
        val password = DEFAULT_PASSWORD
        val destination = createAuthenticatedUser()
        val origin = createAuthenticatedUser(password = password)
        seedRoundTripContent(origin)

        // When: a real export, downloaded over the wire and uploaded into the empty account
        val archive = exportArchiveOf(origin, password)
        val importId = importArchive(destination, archive)

        // Then
        val source = factsOf(origin.user)
        val copy = factsOf(destination.user)
        assertEquals(source.pins.size - 1, copy.pins.size, "the two pins sharing a medium import as one")
        val fromTheSharedMedium =
            copy.pins.keys.count { it != null && (it.endsWith("/$BETA") || it.endsWith("/$DELTA")) }
        assertEquals(1, fromTheSharedMedium, "one of the two lines is skipped, not reported as ambiguous")
        copy.pins.forEach { (sourceContextUrl, imported) ->
            assertSamePin(source.pins.getValue(sourceContextUrl), imported)
        }
        // Named rather than left to the loop: which of the two shared-medium lines survives is the
        // archive's order to decide, and this membership is what the round trip is really about.
        assertEquals(
            setOf("Active board", "Recycled board"),
            copy.pins.getValue("https://example.test/$ALPHA").boardNames,
        )
        val alphaCopy = copy.pins.getValue("https://example.test/$ALPHA")
        assertEquals(PUBLISHED_AT, alphaCopy.publishedAt, "older than the account, and restored unclamped")
        assertEquals(2, alphaCopy.creators.size)
        assertEquals(source.boards, copy.boards, "both boards, the recycled one still recycled")
        assertEquals(source.tags, copy.tags)
        assertEquals(2, copy.collections.size)
        assertEquals(source.collections, copy.collections, "each collection linked to its board, recycled or not")
        // The whole report, not one kind: this is the one case where the real exporter's digest meets
        // the real importer's, so any anomaly at all is a disagreement between the two halves.
        assertEquals(emptyList<String>(), issueKinds(destination, importId))
        val counters = counters(destination, importId)
        assertEquals(3, counters.getInt("createdPins"))
        assertEquals(1, counters.getInt("skippedPins"))
    }

    @Test
    fun `Given an export holding a video, Then it imports into an empty account with the same bytes and hash`() {
        // Given: an MP4, which the import keeps, and a WebM, which it repackages into the same bytes
        for (name in listOf("h264-aac.mkv", "av1.mp4")) {
            val password = DEFAULT_PASSWORD
            val destination = createAuthenticatedUser()
            val origin = createAuthenticatedUser(password = password)
            val pin = createPin(origin, ALPHA)
            given()
                .authenticatedAs(origin)
                .multiPart("file", File("../api-video-ffmpeg/src/test/resources/fixtures/$name"), "video/mp4")
                .`when`()
                .put("/api/v1/pins/${pin.id}/media")
                .then()
                .statusCode(201)

            // When
            importArchive(destination, exportArchiveOf(origin, password))

            // Then
            val source = factsOf(origin.user).pins.values.single()
            val copy = factsOf(destination.user).pins.values.single()
            assertArrayEquals(source.mediaBytes, copy.mediaBytes, "$name should survive byte for byte")
            val sourceMedia = requireNotNull(mediaRepository.findByPinId(source.id))
            val copyMedia = requireNotNull(mediaRepository.findByPinId(copy.id))
            assertEquals(sourceMedia.contentHash, copyMedia.contentHash, name)
            assertEquals(sourceMedia.mimeType, copyMedia.mimeType, name)
        }
    }

    @Test
    fun `Given an archived video whose container its codecs do not choose, Then it is repackaged into that one`() {
        // Given: H.264 with AAC in Matroska, which no export writes and which belongs in MP4 (decision L1)
        val auth = createAuthenticatedUser()
        val mkv = File("../api-video-ffmpeg/src/test/resources/fixtures/h264-aac.mkv").readBytes()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .entry("media/clip.mkv", mkv)
                .pins(
                    ImportArchiveBuilder.pinLine(
                        sourceContextUrl = "https://example.test/clip",
                        mediaPath = "media/clip.mkv",
                        mediaSha256 = ImportArchiveBuilder.sha256(mkv),
                        mediaMimeType = "video/x-matroska",
                    )
                )
                .bytes()

        // When
        importArchive(auth, archive)

        // Then: the stored bytes are an MP4, whose box type sits at offset 4, and the row says so
        val pin = activePinsOf(auth.user).single()
        val media = requireNotNull(mediaRepository.findByPinId(pin.id))
        val stored = mediaStore.openStream(media.storageKey).use { it.readBytes() }
        assertTrue(media.mimeType.startsWith("video/mp4;"), "the type follows the codecs: ${media.mimeType}")
        assertEquals("ftyp", String(stored, 4, 4), "the bytes follow the type")
    }

    @Test
    fun `Given an archived Matroska holding WebM's codecs, Then it is stored as a real WebM`() {
        // Given: VP9 and Opus with a subtitle track, in a Matroska whose doctype is not webm
        val auth = createAuthenticatedUser()
        val mkv = File("../api-video-ffmpeg/src/test/resources/fixtures/subtitled.mkv").readBytes()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .entry("media/clip.mkv", mkv)
                .pins(
                    ImportArchiveBuilder.pinLine(
                        sourceContextUrl = "https://example.test/clip",
                        mediaPath = "media/clip.mkv",
                        mediaSha256 = ImportArchiveBuilder.sha256(mkv),
                        mediaMimeType = "video/x-matroska",
                    )
                )
                .bytes()

        // When
        importArchive(auth, archive)

        // Then: the EBML header names the webm doctype, which a browser's WebM parser requires
        val media = requireNotNull(mediaRepository.findByPinId(activePinsOf(auth.user).single().id))
        val header = mediaStore.openStream(media.storageKey).use { it.readNBytes(HEADER_BYTES) }
        assertTrue(String(header, Charsets.ISO_8859_1).contains("webm"), "the doctype should be webm")
        assertTrue(media.mimeType.startsWith("video/webm;"), media.mimeType)
    }

    private companion object {
        const val HEADER_BYTES = 48
        const val ALPHA = "alpha"
        const val BETA = "beta"
        const val GAMMA = "gamma"
        const val DELTA = "delta"
        val PUBLISHED_AT: Instant = Instant.parse("1999-12-31T23:00:00Z")
    }
}
