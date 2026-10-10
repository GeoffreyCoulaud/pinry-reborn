package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportIssueKind
import io.mockk.every
import io.mockk.verify
import java.net.URI
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `collections.jsonl`, a pin line's `collections` (specification 2026-10-08, decision F); split for `LargeClass`. */
internal class UserDataImportCollectionsTest : UserDataImportRunnerFixtures() {
    private val linked = mutableListOf<RemoteCollection>()

    @Test
    fun `Given a collection naming no board, Then its namesake is created and linked, and a pin line joins it`() {
        // Given: the pin line also names an address no collection holds
        val source =
            FakeArchiveSource(
                manifest = aManifest(),
                collections = listOf(TestLine(1, ImportedCollection(url = FEED_URL, name = "Feed"))),
                pins = listOf(TestLine(1, aPin().inCollections(FEED_URL, UNKNOWN_URL))),
                media = everyMedium,
            )
        stubWalk(source)
        stubMediaPath()
        stubBoardLookup()
        stubBoardCreation()
        stubCollections()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        val board = savedBoard("Feed")
        assertEquals("", board.description)
        assertEquals(now, board.createdAt)
        assertNull(board.softDeletedAt)
        assertEquals(listOf(FEED_URL to board), linked.map { it.url.toString() to it.board })
        assertEquals(now, linked.single().createdAt)
        assertEquals(listOf(board), savedPins.single().boards)
        assertEquals(1, stored.createdBoards)
    }

    @Test
    fun `Given a collection naming an absent board, Then that board is created rather than one of its own name`() {
        // Given
        val line = ImportedCollection(url = FEED_URL, name = "Feed", board = ImportedRef("Elsewhere"))
        stubWalk(FakeArchiveSource(manifest = aManifest(), collections = listOf(TestLine(1, line))))
        stubBoardLookup()
        stubBoardCreation()
        stubCollections()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(listOf("Elsewhere"), savedBoards.map { it.name })
        assertEquals("Elsewhere", linked.single().board.name)
    }

    @Test
    fun `Given a collection naming a recycled board, Then it is linked with no issue and a pin line joins it`() {
        // Given
        val recycled = anExistingBoard("Bin", softDeletedAt = pastInstant)
        val source =
            FakeArchiveSource(
                manifest = aManifest(),
                collections =
                    listOf(TestLine(1, ImportedCollection(url = FEED_URL, name = "Feed", board = ImportedRef("Bin")))),
                pins = listOf(TestLine(1, aPin().inCollections(FEED_URL))),
                media = everyMedium,
            )
        stubWalk(source)
        stubMediaPath()
        stubBoardLookup()
        stubCollections()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(recycled, linked.single().board)
        assertEquals(listOf(recycled), savedPins.single().boards)
        assertEquals(0, stored.issueCount)
    }

    @Test
    fun `Given an address already held, Then its link is kept and a pin line naming it and its board joins once`() {
        // Given
        val held = anExistingBoard("Held")
        val existing =
            RemoteCollection(randomUUID(), user, checkNotNull(HttpUrl.parse(FEED_URL)), "Feed", held, accountCreatedAt)
        val line = ImportedCollection(url = FEED_URL, name = "Renamed", board = ImportedRef("Other"))
        val source =
            FakeArchiveSource(
                manifest = aManifest(),
                collections = listOf(TestLine(1, line)),
                pins = listOf(TestLine(1, aPin(boards = listOf("Held")).inCollections(FEED_URL))),
                media = everyMedium,
            )
        stubWalk(source)
        stubMediaPath()
        stubBoardLookup()
        stubCollectionLookup(existing)

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        verify(exactly = 0) { remoteCollectionRepository.saveRemoteCollection(any()) }
        verify(exactly = 0) { boardRepository.saveBoard(any()) }
        assertEquals(listOf(held), savedPins.single().boards)
    }

    @Test
    fun `Given collection lines past their bounds or malformed, Then each is reported and none is linked`() {
        // Given
        val lines =
            listOf(
                ImportedCollection(url = " ", name = "Feed"),
                ImportedCollection(url = "ftp://remote.test/feed", name = "Feed"),
                ImportedCollection(url = FEED_URL + "x".repeat(OVER_LONG_URL), name = "Feed"),
                ImportedCollection(url = FEED_URL, name = " "),
                ImportedCollection(url = FEED_URL, name = "n".repeat(OVER_LONG_NAME)),
                ImportedCollection(url = FEED_URL, name = "Feed", board = ImportedRef(" ")),
                ImportedCollection(url = FEED_URL, name = "Feed", board = ImportedRef("b".repeat(OVER_LONG_NAME))),
            )
        val malformed = TestLine<ImportedCollection>(lines.size + 1, null, "not a collection")
        val collections = lines.mapIndexed { index, line -> TestLine(index + 1, line) } + malformed
        stubWalk(FakeArchiveSource(manifest = aManifest(), collections = collections))
        stubIssues()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        val expected =
            List(lines.size) { UserDataImportIssueKind.FIELD_INVALID } + UserDataImportIssueKind.LINE_MALFORMED
        assertEquals(expected, kinds())
        assertEquals(expected.size, stored.issueCount)
        assertEquals(
            listOf(
                "url is not an absolute http(s) address",
                "url is not an absolute http(s) address",
                "url is not an absolute http(s) address",
                "name is blank",
                "name is longer than 200 characters",
                "board name is blank",
                "board name is longer than 200 characters",
            ),
            savedIssues.take(lines.size).map { it.detail },
        )
    }

    @Test
    fun `Given a pin line naming 101 collections, Then it is reported invalid and no pin is created`() {
        // Given
        val line = aPin().inCollections(*Array(OVER_LONG_REFS) { "https://remote.test/$it" })
        stubWalk(FakeArchiveSource(manifest = aManifest(), pins = listOf(TestLine(1, line)), media = everyMedium))
        stubIssues()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(listOf(UserDataImportIssueKind.FIELD_INVALID), kinds())
        assertTrue(savedPins.isEmpty())
    }

    @Test
    fun `Given a pin line naming an ftp collection, Then it is reported invalid and no pin is created`() {
        // Given
        val line = aPin().inCollections("ftp://remote.test/feed")
        stubWalk(FakeArchiveSource(manifest = aManifest(), pins = listOf(TestLine(1, line)), media = everyMedium))
        stubIssues()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(listOf("collections is not an absolute http(s) address"), savedIssues.map { it.detail })
        assertTrue(savedPins.isEmpty())
    }

    private fun ImportedPin.inCollections(vararg urls: String) = copy(collections = urls.map(::ImportedCollectionRef))

    private fun stubCollectionLookup(vararg existing: RemoteCollection) {
        // MockK hands the value class over unboxed, as its URI.
        every { remoteCollectionRepository.findUserRemoteCollectionByUrl(user, any()) } answers
            {
                (existing.toList() + linked).firstOrNull { collection -> collection.url.uri == secondArg<URI>() }
            }
    }

    /** A linked collection is one the lookup answers with from then on, as a repository would. */
    private fun stubCollections() {
        stubCollectionLookup()
        every { remoteCollectionRepository.saveRemoteCollection(any()) } answers
            {
                firstArg<RemoteCollection>().also { collection -> linked += collection }
            }
    }

    private companion object {
        const val FEED_URL = "https://remote.test/feed"
        const val UNKNOWN_URL = "https://remote.test/unknown"
        const val OVER_LONG_URL = 2000
    }
}
