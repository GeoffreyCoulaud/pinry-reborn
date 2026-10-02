package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.exports.ArchiveFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.exports.ExportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ImportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.SessionTokenRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataExportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportIssueRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserPasswordHashRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exports.ExportArchiveKey
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class AccountDeletionCleanerTest : BaseTest() {
    private val users = mockk<UserRepositoryInterface>(relaxed = true)
    private val pins = mockk<PinRepositoryInterface>(relaxed = true)
    private val boards = mockk<BoardRepositoryInterface>(relaxed = true)
    private val tags = mockk<TagRepositoryInterface>(relaxed = true)
    private val mediaRepository = mockk<MediaRepositoryInterface>(relaxed = true)
    private val sessions = mockk<SessionTokenRepositoryInterface>(relaxed = true)
    private val passwords = mockk<UserPasswordHashRepositoryInterface>(relaxed = true)
    private val clearDownload = mockk<ClearPinDownload>(relaxed = true)
    private val mediaStore = mockk<MediaStore>(relaxed = true)
    private val renditions = mockk<RenditionCache>(relaxed = true)
    private val exports = mockk<UserDataExportRepositoryInterface>(relaxed = true)
    private val exportArchiveStore = mockk<ExportArchiveStore>(relaxed = true)
    private val imports = mockk<UserDataImportRepositoryInterface>(relaxed = true)
    private val importIssues = mockk<UserDataImportIssueRepositoryInterface>(relaxed = true)
    private val importArchiveStore = mockk<ImportArchiveStore>(relaxed = true)
    private val tx = mockk<TransactionRunner>()
    private val cleaner = AccountDeletionCleaner(
        users, pins, boards, tags, mediaRepository, sessions, passwords, clearDownload, mediaStore, renditions,
        exports, exportArchiveStore, imports, importIssues, importArchiveStore, tx,
    )

    private val userId = randomUUID()
    private val user = User(id = userId, name = "u", softDeletedAt = TestTime.now, createdAt = TestTime.now)

    private fun buildPin() = Pin(
        id = randomUUID(), author = user, sourceContextUrl = "https://ctx",
        sourceMediaUrl = null, description = "desc", tags = emptyList(), boards = emptyList(),
        createdAt = TestTime.now,
        updatedAt = TestTime.now,
    )

    private fun buildMedia(pinId: UUID) = Media(
        id = randomUUID(), pinId = pinId, mimeType = "image/png", width = 1, height = 1,
        animated = false, byteSize = 1, contentHash = "h", storageKey = "originals/x/$pinId/i.png",
        createdAt = Instant.parse("2026-07-10T00:00:00Z"),
    )

    @Test
    fun `Given a tombstoned user with a pin+image, Then everything is erased in order and disk cleaned`() {
        // Given
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        val pin = buildPin()
        every { pins.findAllPinIdsForUser(user) } returns listOf(pin.id)
        val media = buildMedia(pin.id)
        every { mediaRepository.findByPinId(pin.id) } returns media

        // When
        cleaner.deleteAccountData(userId)

        // Then
        verifyOrder {
            clearDownload.clear(pin.id)
            mediaRepository.deleteByPinId(pin.id)
            pins.permanentlyDeleteAllPinsForUser(user)
            boards.permanentlyDeleteAllBoardsForUser(user)
            tags.deleteAllTagsForUser(user)
            sessions.deleteAllForUser(userId)
            passwords.deleteForUser(user)
            users.permanentlyDeleteUser(user)
        }
        verify { mediaStore.delete(media.storageKey) }
        verify { renditions.evictMedia(media.id) }
    }

    @Test
    fun `Given an already-deleted user, Then it is a no-op`() {
        // Given
        every { users.findUserByIdIncludingDeleted(userId) } returns null

        // When
        cleaner.deleteAccountData(userId)

        // Then
        verify(exactly = 0) { users.permanentlyDeleteUser(any()) }
    }

    @Test
    fun `Given a rendition eviction failure, Then it is swallowed`() {
        // Given
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        val pin = buildPin()
        val media = buildMedia(pin.id)
        every { pins.findAllPinIdsForUser(user) } returns listOf(pin.id)
        every { mediaRepository.findByPinId(pin.id) } returns media
        every { renditions.evictMedia(media.id) } throws RuntimeException("disk")

        // When / Then
        assertDoesNotThrow { cleaner.deleteAccountData(userId) } // committed DB, disk best-effort
    }

    @Test
    fun `Given a user with an export, Then its rows are deleted and its derived bytes erased after the commit`() {
        // Given
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        every { pins.findAllPinIdsForUser(user) } returns emptyList()
        val exportId = randomUUID()
        every { exports.findAllExportIdsForUser(userId) } returns listOf(exportId)
        // A format the store does not ship, so the expectation below is a literal rather than a
        // second call to the derivation: an implementation ignoring its extension answers zip here.
        every { exportArchiveStore.format } returns
            ArchiveFormat(mediaType = "application/x-tar", fileExtension = "tar")

        // When
        cleaner.deleteAccountData(userId)

        // Then
        verifyOrder {
            exports.deleteAllForUser(userId)
            users.permanentlyDeleteUser(user)
        }
        // The key is DERIVED from the id, not read from the row, so a build that died before writing
        // its row (leaving a promoted file with no storageKey column) is still reclaimed.
        verify { exportArchiveStore.delete("exports/$exportId.tar") }
    }

    @Test
    fun `Given a user with no export, Then no archive delete is attempted`() {
        // Given
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        every { pins.findAllPinIdsForUser(user) } returns emptyList()
        every { exports.findAllExportIdsForUser(userId) } returns emptyList()

        // When
        cleaner.deleteAccountData(userId)

        // Then
        verify { exports.deleteAllForUser(userId) }
        verify(exactly = 0) { exportArchiveStore.delete(any()) }
    }

    @Test
    fun `Given a user with an import, Then its rows go before the user and its bytes after the commit`() {
        // Given: the transaction says where it is, so "after the commit" is asserted rather than named.
        // A disk pass held inside it keeps the one writer connection for the length of an unlink.
        var insideTransaction = false
        every { tx.inTransaction(any<() -> Any?>()) } answers {
            insideTransaction = true
            (firstArg<() -> Any?>())().also { insideTransaction = false }
        }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        every { pins.findAllPinIdsForUser(user) } returns emptyList()
        every { exports.findAllExportIdsForUser(userId) } returns emptyList()
        val importId = randomUUID()
        every { imports.findAllImportIdsForUser(userId) } returns listOf(importId)
        var archiveDeletedInside: Boolean? = null
        every { importArchiveStore.delete(any()) } answers { archiveDeletedInside = insideTransaction }
        var uploadDiscardedInside: Boolean? = null
        every { importArchiveStore.discardPartialUpload(any()) } answers {
            uploadDiscardedInside = insideTransaction
        }

        // When
        cleaner.deleteAccountData(userId)

        // Then: the issues before the rows they hang off, and both before the user row
        verifyOrder {
            importIssues.deleteAllForUser(userId)
            imports.deleteAllForUser(userId)
            users.permanentlyDeleteUser(user)
        }
        // Derived from the id, not read from the row, so a completer that died after its promote and
        // before its row write is still reclaimed. The upload goes too, promoted or not.
        verify { importArchiveStore.delete("imports/$importId.zip") }
        verify { importArchiveStore.discardPartialUpload(importId) }
        assertEquals(false, archiveDeletedInside, "the archive delete must not hold the transaction")
        assertEquals(false, uploadDiscardedInside, "the upload discard must not hold the transaction")
    }

    @Test
    fun `Given a user with no import, Then no import archive delete is attempted`() {
        // Given
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        every { pins.findAllPinIdsForUser(user) } returns emptyList()
        every { exports.findAllExportIdsForUser(userId) } returns emptyList()
        every { imports.findAllImportIdsForUser(userId) } returns emptyList()

        // When
        cleaner.deleteAccountData(userId)

        // Then
        verify { imports.deleteAllForUser(userId) }
        verify(exactly = 0) { importArchiveStore.delete(any()) }
        verify(exactly = 0) { importArchiveStore.discardPartialUpload(any()) }
    }

    @Test
    fun `Given an import archive delete that throws, Then the rest of the disk pass still runs`() {
        // Given: the rows are committed, so the disk pass is best effort and the sweep is the guarantor
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        every { pins.findAllPinIdsForUser(user) } returns emptyList()
        every { exports.findAllExportIdsForUser(userId) } returns emptyList()
        val firstImportId = randomUUID()
        val secondImportId = randomUUID()
        every { imports.findAllImportIdsForUser(userId) } returns listOf(firstImportId, secondImportId)
        every { importArchiveStore.delete(any()) } throws RuntimeException("disk down")

        // When / Then
        assertDoesNotThrow { cleaner.deleteAccountData(userId) }
        verify { importArchiveStore.delete("imports/$secondImportId.zip") }
        verify { importArchiveStore.discardPartialUpload(secondImportId) }
    }

    @Test
    fun `Given a pin with no image, Then it is cleared and deleted but nothing is evicted`() {
        // Given
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        val pin = buildPin()
        every { pins.findAllPinIdsForUser(user) } returns listOf(pin.id)
        every { mediaRepository.findByPinId(pin.id) } returns null

        // When
        cleaner.deleteAccountData(userId)

        // Then
        verify { clearDownload.clear(pin.id) }
        verify { mediaRepository.deleteByPinId(pin.id) }
        verify(exactly = 0) { mediaStore.delete(any()) }
        verify(exactly = 0) { renditions.evictMedia(any()) }
    }

    @Test
    fun `Given mediaStore delete throws mid-loop, Then the disk loop still attempts all images and exports`() {
        // Given: at least two pins with images and one export archive, so the loop has a
        // second iteration plus the export loop to reach after the throw.
        every { tx.inTransaction(any<() -> Any?>()) } answers { (firstArg<() -> Any?>())() }
        every { users.findUserByIdIncludingDeleted(userId) } returns user
        val firstPin = buildPin()
        val secondPin = buildPin()
        val firstMedia = buildMedia(firstPin.id)
        val secondMedia = buildMedia(secondPin.id)
        every { pins.findAllPinIdsForUser(user) } returns listOf(firstPin.id, secondPin.id)
        every { mediaRepository.findByPinId(firstPin.id) } returns firstMedia
        every { mediaRepository.findByPinId(secondPin.id) } returns secondMedia
        val exportId = randomUUID()
        every { exports.findAllExportIdsForUser(userId) } returns listOf(exportId)
        every { exportArchiveStore.format } returns ArchiveFormat(mediaType = "application/zip", fileExtension = "zip")
        every { mediaStore.delete(any()) } throws RuntimeException("disk down")

        // When / Then: the throw must not abort the loop, and the export loop must still run.
        assertDoesNotThrow { cleaner.deleteAccountData(userId) }
        verify { mediaStore.delete(firstMedia.storageKey) }
        verify { mediaStore.delete(secondMedia.storageKey) }
        verify { renditions.evictMedia(firstMedia.id) }
        verify { renditions.evictMedia(secondMedia.id) }
        verify { exportArchiveStore.delete(ExportArchiveKey.forExport(exportId, "zip")) }
    }
}
