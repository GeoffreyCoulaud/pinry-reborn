package fr.geoffreyCoulaud.pinryReborn.api.application

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
        assertEquals(listOf("https://example.test/good"), activePinsOf(auth.user).map { it.sourceContextUrl })
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
