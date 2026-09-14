package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanImageDownloadRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.ebean.test.LoggedSql
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class EbeanImageDownloadRepositoryTest : RepositoryTest() {
    private val repository = EbeanImageDownloadRepository(persistor)
    private val pins = PinRepository(persistor)
    private val users = UserRepository(persistor)
    private val now = Instant.parse("2026-07-10T00:00:00Z")

    private fun saveUser(): User = users.saveUser(User(randomUUID(), createRandomString(), createdAt = now))

    private fun failedAt(updatedAt: Instant): UUID {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/i.png", randomUUID(), updatedAt)
        repository.markFailed(pinId, DownloadReason.NOT_FOUND, updatedAt)
        return pinId
    }

    private fun savePin(author: User): Pin =
        pins.savePin(
            Pin(randomUUID(), author, "https://example.com", null, "d", emptyList(), emptyList(), now, now),
        )

    @Test
    fun `Given upsertPending, Then findByPinId returns a PENDING row`() {
        val pinId = randomUUID()
        val saved = repository.upsertPending(pinId, "https://x/i.png", randomUUID(), now)
        assertEquals(DownloadStatus.PENDING, saved.status)
        assertEquals(saved, repository.findByPinId(pinId))
    }

    @Test
    fun `Given an existing row, Then upsertPending replaces it with a fresh PENDING`() {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/a.png", randomUUID(), now)
        repository.markFailed(pinId, DownloadReason.NOT_FOUND, now)
        val replaced = repository.upsertPending(pinId, "https://x/b.png", randomUUID(), now)
        assertEquals("https://x/b.png", replaced.sourceUrl)
        assertEquals(DownloadStatus.PENDING, repository.findByPinId(pinId)?.status)
        assertNull(repository.findByPinId(pinId)?.reasonCode)
    }

    @Test
    fun `Given a PENDING row, Then markFailed sets FAILED and returns true`() {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/i.png", randomUUID(), now)
        assertTrue(repository.markFailed(pinId, DownloadReason.ACCESS_DENIED, now))
        val row = repository.findByPinId(pinId)
        assertEquals(DownloadStatus.FAILED, row?.status)
        assertEquals(DownloadReason.ACCESS_DENIED, row?.reasonCode)
    }

    @Test
    fun `Given no PENDING row, Then markFailed returns false`() {
        assertFalse(repository.markFailed(randomUUID(), DownloadReason.ACCESS_DENIED, now))
    }

    @Test
    fun `Given a PENDING row, Then recordLastError keeps PENDING and returns true`() {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/i.png", randomUUID(), now)
        assertTrue(repository.recordLastError(pinId, "timeout", now))
        assertEquals(DownloadStatus.PENDING, repository.findByPinId(pinId)?.status)
        assertEquals("timeout", repository.findByPinId(pinId)?.lastError)
    }

    @Test
    fun `Given no PENDING row, Then recordLastError returns false`() {
        assertFalse(repository.recordLastError(randomUUID(), "x", now))
    }

    @Test
    fun `Given a PENDING row, Then deleteIfPending deletes it and returns 1`() {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/i.png", randomUUID(), now)
        assertEquals(1, repository.deleteIfPending(pinId))
        assertNull(repository.findByPinId(pinId))
    }

    @Test
    fun `Given a FAILED row, Then deleteIfPending returns 0 and keeps the row`() {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/i.png", randomUUID(), now)
        repository.markFailed(pinId, DownloadReason.NOT_FOUND, now)
        assertEquals(0, repository.deleteIfPending(pinId))
        assertEquals(DownloadStatus.FAILED, repository.findByPinId(pinId)?.status)
    }

    @Test
    fun `Given any row, Then deleteByPinId removes it and is a no-op when absent`() {
        val pinId = randomUUID()
        repository.upsertPending(pinId, "https://x/i.png", randomUUID(), now)
        repository.deleteByPinId(pinId)
        assertNull(repository.findByPinId(pinId))
        repository.deleteByPinId(randomUUID()) // must not throw
    }

    @Test
    fun `Given FAILED rows either side of the cutoff, Then deleteFailedBefore removes only the older`() {
        // Given
        val stale = failedAt(now)
        val recent = failedAt(now.plusSeconds(SIXTY_SECONDS))
        val pending = repository.upsertPending(randomUUID(), "https://x/i.png", randomUUID(), now).pinId

        // When
        val deleted = repository.deleteFailedBefore(now.plusSeconds(1))

        // Then: the cutoff is read against updatedAt, and a PENDING row is never deleted
        assertEquals(1, deleted)
        assertNull(repository.findByPinId(stale))
        assertEquals(DownloadStatus.FAILED, repository.findByPinId(recent)?.status)
        assertEquals(DownloadStatus.PENDING, repository.findByPinId(pending)?.status)
    }

    @Test
    fun `Given rows in both states, Then findPending returns the PENDING ones with their task id`() {
        // Given
        val taskId = randomUUID()
        val pending = repository.upsertPending(randomUUID(), "https://x/i.png", taskId, now)
        failedAt(now)

        // When
        val found = repository.findPending()

        // Then: the sweep matches a row to its task, so the task id has to come back with it
        assertEquals(listOf(pending), found)
        assertEquals(taskId, found.single().taskId)
    }

    @Test
    fun `Given a recycled pin carrying a row, Then the sweep's two reads still reach it`() {
        // Given: the sweep is about the row, not about what the requester can see, so unlike
        // findByAuthor it filters on no pin state
        val author = saveUser()
        val recycled = savePin(author)
        repository.upsertPending(recycled.id, "https://x/i.png", randomUUID(), now)
        pins.softDeletePin(recycled, now)

        // When
        val found = repository.findPending()
        repository.markFailed(recycled.id, DownloadReason.INTERNAL_ERROR, now)
        val deleted = repository.deleteFailedBefore(now.plusSeconds(1))

        // Then
        assertEquals(listOf(recycled.id), found.map { it.pinId })
        assertEquals(1, deleted)
        assertNull(repository.findByPinId(recycled.id))
    }

    @Test
    fun `Given pins with and without a download, Then findByPinIds keys the rows it finds by pin id`() {
        // Given
        val downloading = randomUUID()
        val bare = randomUUID()
        val row = repository.upsertPending(downloading, "https://x/i.png", randomUUID(), now)

        // When
        val found = repository.findByPinIds(listOf(downloading, bare))

        // Then
        assertEquals(mapOf(downloading to row), found)
    }

    @Test
    fun `Given no pin ids, Then findByPinIds returns an empty map`() {
        // Given
        repository.upsertPending(randomUUID(), "https://x/i.png", randomUUID(), now)

        // When
        val found = repository.findByPinIds(emptyList())

        // Then
        assertTrue(found.isEmpty())
    }

    @Test
    fun `Given running and failed downloads of the author, Then findByAuthor returns both newest first`() {
        // Given
        val author = saveUser()
        val failed = savePin(author)
        val running = savePin(author)
        repository.upsertPending(failed.id, "https://x/old.png", randomUUID(), now)
        repository.markFailed(failed.id, DownloadReason.NOT_FOUND, now)
        repository.upsertPending(running.id, "https://x/new.png", randomUUID(), now.plusSeconds(SIXTY_SECONDS))

        // When
        val found = repository.findByAuthor(author.id, cursor = null, pageSize = PAGE_SIZE)

        // Then
        assertEquals(listOf(running.id, failed.id), found.items.map { it.pinId })
        assertEquals(listOf(DownloadStatus.PENDING, DownloadStatus.FAILED), found.items.map { it.status })
        assertNull(found.nextCursor, "One page holds both rows, so nothing follows it")
    }

    @Test
    fun `Given more downloads than the page holds, Then findByAuthor walks them through its cursors`() {
        // Given: three downloads of one author, each a minute newer than the last
        val author = saveUser()
        val pins = (0..2).map { savePin(author) }
        pins.forEachIndexed { index, pin ->
            val requestedAt = now.plusSeconds(index * SIXTY_SECONDS)
            repository.upsertPending(pin.id, "https://x/$index.png", randomUUID(), requestedAt)
        }

        // When: the first page of two, then the page after it, then back again
        val first = repository.findByAuthor(author.id, cursor = null, pageSize = 2)
        val second = repository.findByAuthor(author.id, cursor = first.nextCursor, pageSize = 2)
        val back = repository.findByAuthor(author.id, cursor = second.previousCursor, pageSize = 2)

        // Then: newest first across the pages, and the walk back lands on the first page again
        assertEquals(listOf(pins[2].id, pins[1].id), first.items.map { it.pinId })
        assertEquals(listOf(pins[0].id), second.items.map { it.pinId })
        assertNull(second.nextCursor)
        assertEquals(first.items.map { it.pinId }, back.items.map { it.pinId })
    }

    @Test
    fun `Given a cursor whose pivot is gone, Then findByAuthor answers the first page`() {
        // Given: the row the cursor pivots on was dropped between two pages, which the sweep and
        // the user's own delete both do
        val author = saveUser()
        val kept = savePin(author)
        repository.upsertPending(kept.id, "https://x/i.png", randomUUID(), now)
        val stale = Cursor(pivotId = randomUUID(), direction = CursorDirection.FORWARD)

        // When
        val found = repository.findByAuthor(author.id, cursor = stale, pageSize = PAGE_SIZE)

        // Then: no pivot, so no keyset filter, and the walk restarts rather than answering nothing
        assertEquals(listOf(kept.id), found.items.map { it.pinId })
    }

    @Test
    fun `Given downloads sharing one requestedAt, Then findByAuthor still advances past them`() {
        // Given: the pair (requestedAt, id) is what orders a page; on requestedAt alone a page
        // boundary inside a group sharing the instant stalls the cursor (PinModelSortStrategy's bug)
        val author = saveUser()
        val pins = (0..2).map { savePin(author) }
        pins.forEach { repository.upsertPending(it.id, "https://x/i.png", randomUUID(), now) }

        // When
        val first = repository.findByAuthor(author.id, cursor = null, pageSize = 2)
        val second = repository.findByAuthor(author.id, cursor = first.nextCursor, pageSize = 2)

        // Then: every row is seen exactly once across the two pages
        val walked = first.items.map { it.pinId } + second.items.map { it.pinId }
        assertEquals(pins.map { it.id }.toSet(), walked.toSet())
        assertEquals(walked.size, walked.toSet().size)
    }

    @Test
    fun `Given a recycled pin carrying a download, Then findByAuthor omits its row`() {
        // Given
        val author = saveUser()
        val recycled = savePin(author)
        repository.upsertPending(recycled.id, "https://x/i.png", randomUUID(), now)
        pins.softDeletePin(recycled, now)

        // When / Then
        assertTrue(repository.findByAuthor(author.id, cursor = null, pageSize = PAGE_SIZE).items.isEmpty())
    }

    @Test
    fun `Given the author's downloads, Then findByAuthor reads a first page through a single statement`() {
        // Given
        val author = saveUser()
        repository.upsertPending(savePin(author).id, "https://x/i.png", randomUUID(), now)

        // When
        LoggedSql.start()
        repository.findByAuthor(author.id, cursor = null, pageSize = PAGE_SIZE)
        val statements = LoggedSql.stop()

        // Then: the pivot lookup a cursor needs is the second statement, and a first page has none
        assertEquals(1, statements.size, "Expected one statement, ran ${statements.size}: $statements")
    }

    @Test
    fun `Given another author's download, Then findByAuthor omits its row`() {
        // Given
        repository.upsertPending(savePin(saveUser()).id, "https://x/i.png", randomUUID(), now)

        // When / Then
        assertTrue(repository.findByAuthor(saveUser().id, cursor = null, pageSize = PAGE_SIZE).items.isEmpty())
    }

    @Test
    fun `Given a download of the author's pin, Then findByAuthorAndPin returns that row`() {
        // Given
        val author = saveUser()
        val pin = savePin(author)
        val row = repository.upsertPending(pin.id, "https://x/i.png", randomUUID(), now)
        repository.upsertPending(savePin(author).id, "https://x/other.png", randomUUID(), now)

        // When / Then
        assertEquals(row, repository.findByAuthorAndPin(author.id, pin.id))
    }

    @Test
    fun `Given another author's download, Then findByAuthorAndPin returns null`() {
        // Given
        val pin = savePin(saveUser())
        repository.upsertPending(pin.id, "https://x/i.png", randomUUID(), now)

        // When / Then
        assertNull(repository.findByAuthorAndPin(saveUser().id, pin.id))
    }

    @Test
    fun `Given a recycled pin carrying a download, Then findByAuthorAndPin returns null`() {
        // Given
        val author = saveUser()
        val recycled = savePin(author)
        repository.upsertPending(recycled.id, "https://x/i.png", randomUUID(), now)
        pins.softDeletePin(recycled, now)

        // When / Then
        assertNull(repository.findByAuthorAndPin(author.id, recycled.id))
    }

    private companion object {
        const val SIXTY_SECONDS = 60L
        const val PAGE_SIZE = 20
    }
}
