package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPersonModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QRemoteCollectionModel
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(MeImportTestProfile::class)
class MeImportArchiveContentIntegrationTest : MeImportFixtures() {
    @Inject lateinit var mediaConfig: MediaConfig

    // --- One archive, one of every anomaly ---

    @Test
    fun `Given an archive of anomalies, Then it completes, keeps the good pin and reports each fault once`() {
        // Given
        val auth = createAuthenticatedUser()
        val png = fixture("sample.png").readBytes()
        val text = fixture("not-an-image.txt").readBytes()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 5)
                .tags("nature")
                .boards(ImportArchiveBuilder.boardLine(name = "a".repeat(OVER_LONG_NAME)))
                .entry("media/good.png", png)
                .entry("media/text.jpg", text)
                .pins(
                    ImportArchiveBuilder.pinLine(
                        sourceContextUrl = "https://example.test/good",
                        tags = listOf("nature"),
                        mediaPath = "media/good.png",
                        mediaSha256 = ImportArchiveBuilder.sha256(png),
                    ),
                    ImportArchiveBuilder.pinLine("https://example.test/traversal", mediaPath = "../escape.png"),
                    ImportArchiveBuilder.pinLine("https://example.test/absent", mediaPath = "media/absent.png"),
                    ImportArchiveBuilder.pinLine(
                        sourceContextUrl = "https://example.test/text",
                        mediaPath = "media/text.jpg",
                        mediaSha256 = ImportArchiveBuilder.sha256(text),
                    ),
                    ImportArchiveBuilder.pinLine("https://example.test/nomedia"),
                )
                .appendLine("pins.jsonl", "{\"description\": \"cut in ha")
                .bytes()

        // When
        val importId = importArchive(auth, archive)

        // Then: the whole report, so a seventh issue no one asked for fails here. Sorted rather than
        // in the walk's order, which is the report's paging to decide and not what this case is about.
        assertEquals(EXPECTED_ANOMALIES.sorted(), issueKinds(auth, importId).sorted())
        assertEquals(listOf("https://example.test/good"), activePinsOf(auth.user).map { "${it.sourceContextUrl}" })
        assertTrue(boardRepository.findActiveBoardsForUser(auth.user).isEmpty(), "the over-long name is refused")
    }

    // --- The manifest is never the authority ---

    @Test
    fun `Given a line lying about its medium, Then the bytes decide the type and the digest is reported`() {
        // Given: PNG bytes announced as JPEG, under a digest that is not theirs
        val auth = createAuthenticatedUser()
        val png = fixture("sample.png").readBytes()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .tags()
                .boards()
                .entry("media/lying.png", png)
                .pins(
                    ImportArchiveBuilder.pinLine(
                        sourceContextUrl = "https://example.test/lying",
                        mediaPath = "media/lying.png",
                        mediaSha256 = ImportArchiveBuilder.sha256("not these bytes".toByteArray()),
                        mediaMimeType = "image/jpeg",
                    )
                )
                .bytes()

        // When
        val importId = importArchive(auth, archive)

        // Then
        val pin = activePinsOf(auth.user).single()
        val media = requireNotNull(mediaRepository.findByPinId(pin.id))
        assertEquals("image/png", media.mimeType, "the probe decides the stored type, never the archive")
        assertEquals(listOf("MEDIA_DIGEST_MISMATCH"), issueKinds(auth, importId))
        assertArrayEquals(png, mediaStore.openStream(media.storageKey).use { it.readBytes() })
    }

    // --- A medium two pins already hold ---

    @Test
    fun `Given two pins already holding the medium, Then the line is ambiguous and nothing is written`() {
        // Given: the same bytes under two pins of the target account
        val auth = createAuthenticatedUser()
        val png = fixture("sample.png").readBytes()
        listOf("first", "second").forEach { slug ->
            uploadMedia(auth, createPin(auth, slug).id, "sample.png", "image/png")
        }
        val storedBefore = storedObjectCount(auth.user.id)
        awaitFingerprintDrain()
        // Scoped like the count above: `media.data_dir` outlives a case and is shared with the sweep
        // suite, which runs on this profile too, so emptiness would be another suite's business.
        val stagedBefore = stagedFiles()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .tags()
                .boards()
                .entry("media/ambiguous.png", png)
                .pins(
                    ImportArchiveBuilder.pinLine(
                        sourceContextUrl = "https://example.test/ambiguous",
                        mediaPath = "media/ambiguous.png",
                        mediaSha256 = ImportArchiveBuilder.sha256(png),
                    )
                )
                .bytes()

        // When
        val importId = importArchive(auth, archive)

        // Then: no row, no promoted object, no staged temp file
        assertEquals(listOf("MEDIA_AMBIGUOUS"), issueKinds(auth, importId))
        assertEquals(2, activePinsOf(auth.user).size)
        assertEquals(2, storedBefore, "the two uploads are what the count compares against")
        assertEquals(storedBefore, storedObjectCount(auth.user.id), "an ambiguous line promotes nothing")
        assertEquals(stagedBefore, stagedFiles(), "an ambiguous line stages nothing")
    }

    // --- An account that is not empty ---

    @Test
    fun `Given names the account already holds in another case, Then nothing is created and nothing changes`() {
        // Given: the account holds tag `voyage` and board `Summer`, the archive carries `Voyage` and `summer`
        val auth = createAuthenticatedUser()
        val pin = createPin(auth, "held", tags = listOf("voyage"))
        val board = boardCreator.create(auth.user, "Summer", "the account's own")
        replacePin(auth, pin, boardIds = listOf(board.id)).statusCode(200)
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 0)
                .tags("Voyage")
                .boards(ImportArchiveBuilder.boardLine(name = "summer", description = "the archive's"))
                .pins()
                .bytes()

        // When
        val importId = importArchive(auth, archive)

        // Then
        val counters = counters(auth, importId)
        assertEquals(0, counters.getInt("createdTags"))
        assertEquals(1, counters.getInt("skippedTags"))
        assertEquals(0, counters.getInt("createdBoards"))
        assertEquals(1, counters.getInt("skippedBoards"))
        assertEquals(setOf("voyage"), tagRepository.findAllTagsForUser(auth.user).map { it.name }.toSet())
        val kept = boardRepository.findActiveBoardsForUser(auth.user).single()
        assertEquals(board.name, kept.name)
        assertEquals(board.description, kept.description)
        assertEquals(board.updatedAt, kept.updatedAt, "a skipped board is left untouched")
        assertEquals(setOf(board.name), pinRepository.findBoardsForPinIncludingRecycled(pin.id).map { it.name }.toSet())
    }

    @Test
    fun `Given a recycled board holding the name, Then the archive's board is refused and the bin is untouched`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(auth.user, "Summer", "recycled")
        given().authenticatedAs(auth).`when`().delete("/api/v1/boards/${board.id}").then().statusCode(204)
        val recycled = boardRepository.findRecycledBoardsForUser(auth.user).single()
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 0)
                .tags()
                .boards(ImportArchiveBuilder.boardLine(name = "Summer", description = "the archive's"))
                .pins()
                .bytes()

        // When
        val importId = importArchive(auth, archive)

        // Then
        assertEquals(listOf("NAME_TAKEN_BY_RECYCLED"), issueKinds(auth, importId))
        assertTrue(boardRepository.findActiveBoardsForUser(auth.user).isEmpty(), "nothing is created")
        assertEquals(recycled, boardRepository.findRecycledBoardsForUser(auth.user).single())
    }

    // --- A pin line's people ---

    @Test
    fun `Given person lines in two cases and two orders, Then one row, and one address more makes a second`() {
        // Given: three media, so no line is skipped as one the account already holds
        val auth = createAuthenticatedUser()
        val creatorByMedium = mapOf("sample.png" to "ada", "sample.jpg" to "ADA", "animated.gif" to "ada-c")
        val builder =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = creatorByMedium.size)
                .persons(
                    ImportArchiveBuilder.personLine("ada", "Ada Lovelace", A_URL, B_URL),
                    ImportArchiveBuilder.personLine("ADA", "ada LOVELACE", B_URL, A_URL),
                    ImportArchiveBuilder.personLine("ada-c", "Ada Lovelace", A_URL, B_URL, "https://c.example"),
                )
        val lines = creatorByMedium.map { (medium, creator) ->
            val bytes = fixture(medium).readBytes()
            builder.entry("media/$medium", bytes)
            ImportArchiveBuilder.pinLine(
                sourceContextUrl = "https://example.test/$medium",
                mediaPath = "media/$medium",
                mediaSha256 = ImportArchiveBuilder.sha256(bytes),
                creators = listOf(ImportArchiveBuilder.personRef(creator)),
            )
        }

        // When
        val importId = importArchive(auth, builder.pins(*lines.toTypedArray()).bytes())

        // Then
        assertEquals(emptyList<String>(), issueKinds(auth, importId))
        val creatorOf = activePinsOf(auth.user).associate { it.sourceContextUrl.toString() to it.creators.single() }
        assertEquals(
            creatorOf.getValue("https://example.test/sample.png").id,
            creatorOf.getValue("https://example.test/sample.jpg").id,
        )
        assertEquals(3, creatorOf.getValue("https://example.test/animated.gif").urls.size)
        assertEquals(2, QPersonModel().author.id.equalTo(auth.user.id).findCount())
    }

    @Test
    fun `Given a pin of 101 creators and one of two absent ids, Then the first is refused, the second reported`() {
        // Given: the refused line is refused before its medium is read, so it shares the second's
        val auth = createAuthenticatedUser()
        val png = fixture("sample.png").readBytes()
        val line = { slug: String, publisher: Map<String, Any?>?, creators: List<Map<String, Any?>> ->
            ImportArchiveBuilder.pinLine(
                sourceContextUrl = "https://example.test/$slug",
                mediaPath = "media/only.png",
                mediaSha256 = ImportArchiveBuilder.sha256(png),
                publisher = publisher,
                creators = creators,
            )
        }
        val archive =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 2)
                .entry("media/only.png", png)
                .pins(
                    line("crowd", null, List(OVER_LONG_REFS) { ImportArchiveBuilder.personRef("p$it") }),
                    line(
                        "ghosts",
                        ImportArchiveBuilder.personRef("ghost"),
                        listOf(ImportArchiveBuilder.personRef("phantom")),
                    ),
                )
                .bytes()

        // When
        val importId = importArchive(auth, archive)

        // Then
        assertEquals(listOf("FIELD_INVALID", "PERSON_UNKNOWN", "PERSON_UNKNOWN"), issueKinds(auth, importId).sorted())
        assertEquals(setOf("ghost", "phantom"), unknownPersons(auth, importId).toSet())
        val pin = activePinsOf(auth.user).single()
        assertEquals("https://example.test/ghosts", pin.sourceContextUrl.toString())
        assertNull(pin.publisher)
        assertTrue(pin.creators.isEmpty())
        assertEquals(0, QPersonModel().author.id.equalTo(auth.user.id).findCount())
    }

    // --- Collections ---

    @Test
    fun `Given collections naming no board or an absent one, Then each board is created, linked and joined`() {
        // Given: the second and third collections share the board `Shared`, which the account lacks
        val auth = createAuthenticatedUser()
        val builder =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 2)
                .collections(
                    ImportArchiveBuilder.collectionLine(FEED_URL, "Feed"),
                    ImportArchiveBuilder.collectionLine(OTHER_URL, "Other", board = "Shared"),
                    ImportArchiveBuilder.collectionLine(THIRD_URL, "Third", board = "Shared"),
                )
        builder.pins(pinNaming(builder, "sample.png", FEED_URL), pinNaming(builder, "sample.jpg", THIRD_URL))

        // When
        val importId = importArchive(auth, builder.bytes())

        // Then
        assertEquals(emptyList<String>(), issueKinds(auth, importId))
        assertEquals(listOf("Feed", "Shared"), boardRepository.findActiveBoardsForUser(auth.user).map { it.name })
        assertEquals(
            mapOf(FEED_URL to "Feed", OTHER_URL to "Shared", THIRD_URL to "Shared"),
            QRemoteCollectionModel().findList().associate { it.url to it.board.name },
        )
        assertEquals(
            mapOf("https://example.test/sample.png" to "Feed", "https://example.test/sample.jpg" to "Shared"),
            activePinsOf(auth.user).associate { it.sourceContextUrl.toString() to it.boards.single().name },
        )
    }

    @Test
    fun `Given a collection imported again after its board is renamed, Then a new pin line joins the renamed board`() {
        // Given
        val auth = createAuthenticatedUser()
        val collection = ImportArchiveBuilder.collectionLine(FEED_URL, "Feed")
        importArchive(
            auth,
            ImportArchiveBuilder(objectMapper).manifest(announcedPins = 0).collections(collection).bytes(),
        )
        val board = boardRepository.findActiveBoardsForUser(auth.user).single()
        boardRepository.saveBoard(board.copy(name = "Renamed"))
        val builder = ImportArchiveBuilder(objectMapper).manifest(announcedPins = 1).collections(collection)
        builder.pins(pinNaming(builder, "sample.png", FEED_URL))

        // When
        val importId = importArchive(auth, builder.bytes())

        // Then
        assertEquals(emptyList<String>(), issueKinds(auth, importId))
        assertEquals(listOf("Renamed"), boardRepository.findActiveBoardsForUser(auth.user).map { it.name })
        assertEquals(listOf(board.id), activePinsOf(auth.user).single().boards.map { it.id })
    }

    @Test
    fun `Given a collection naming a recycled board, Then it is linked with no issue and a pin line joins the board`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(auth.user, "Bin", "")
        given().authenticatedAs(auth).`when`().delete("/api/v1/boards/${board.id}").then().statusCode(204)
        val builder =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .collections(ImportArchiveBuilder.collectionLine(FEED_URL, "Feed", board = "Bin"))
        builder.pins(pinNaming(builder, "sample.png", FEED_URL))

        // When
        val importId = importArchive(auth, builder.bytes())

        // Then
        assertEquals(emptyList<String>(), issueKinds(auth, importId))
        assertEquals(board.id, QRemoteCollectionModel().findList().single().board.id)
        val pin = activePinsOf(auth.user).single()
        assertEquals(listOf(board.id), pinRepository.findBoardsForPinIncludingRecycled(pin.id).map { it.id })
    }

    @Test
    fun `Given a collection line in capitals, Then a pin line naming its normalised address joins its board`() {
        // Given
        val auth = createAuthenticatedUser()
        val builder =
            ImportArchiveBuilder(objectMapper)
                .manifest(announcedPins = 1)
                .collections(ImportArchiveBuilder.collectionLine("HTTPS://X.test/c", "Feed"))
        builder.pins(pinNaming(builder, "sample.png", "https://x.test/c"))

        // When
        val importId = importArchive(auth, builder.bytes())

        // Then
        assertEquals(emptyList<String>(), issueKinds(auth, importId))
        assertEquals(listOf("https://x.test/c"), QRemoteCollectionModel().findList().map { it.url })
        assertEquals(listOf("Feed"), activePinsOf(auth.user).single().boards.map { it.name })
    }

    /** A pin line over its own medium, so no line is skipped as one the account already holds. */
    private fun pinNaming(
        builder: ImportArchiveBuilder,
        medium: String,
        vararg collections: String,
    ): Map<String, Any?> {
        val bytes = fixture(medium).readBytes()
        builder.entry("media/$medium", bytes)
        return ImportArchiveBuilder.pinLine(
            sourceContextUrl = "https://example.test/$medium",
            mediaPath = "media/$medium",
            mediaSha256 = ImportArchiveBuilder.sha256(bytes),
            collections = collections.toList(),
        )
    }

    /** The subjects of the `PERSON_UNKNOWN` issues, which the report does not order within a line. */
    private fun unknownPersons(auth: AuthenticatedUser, importId: UUID): List<String> =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/me/imports/$importId/issues?pageSize=$ISSUE_PAGE_SIZE")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("issues.findAll { it.kind == 'PERSON_UNKNOWN' }.subject", String::class.java)

    /** Scoped to the account: the data directory outlives a case, since only the database is truncated. */
    private fun storedObjectCount(userId: UUID): Int =
        countFiles(Path.of(mediaConfig.dataDir()).resolve("originals").resolve(userId.toString()))

    private fun stagedFiles(): List<Path> = listFiles(Path.of(mediaConfig.dataDir()).resolve("tmp"))

    private fun countFiles(root: Path): Int = listFiles(root).size

    private fun listFiles(root: Path): List<Path> =
        when {
            !root.exists() -> emptyList()
            else -> Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() }
        }

    private companion object {
        const val OVER_LONG_NAME = 300
        const val OVER_LONG_REFS = 101
        const val A_URL = "https://a.example"
        const val B_URL = "https://b.example"
        const val FEED_URL = "https://remote.test/feed"
        const val OTHER_URL = "https://remote.test/other"
        const val THIRD_URL = "https://remote.test/third"
        val EXPECTED_ANOMALIES =
            listOf(
                "ENTRY_PATH_INVALID",
                "LINE_MALFORMED",
                "MEDIA_ENTRY_MISSING",
                "MEDIA_UNREADABLE",
                "PIN_HAS_NO_MEDIA",
                "FIELD_INVALID",
            )
    }
}
