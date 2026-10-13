package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.boardName
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.collectionName
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.personName
import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.tagName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataExport
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImport
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImportIssue
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportIssueKind
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaFrameRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataExportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportIssueRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QTaskModel
import fr.geoffreyCoulaud.pinryReborn.api.usecases.AccountDeletionCleaner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.EnqueueTask
import io.ebean.DB
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The cleaner deletes the user row, and the database's keys delete every row that depends on it (ADR 0058). */
@QuarkusTest
class AccountDeletionCascadeIntegrationTest : IntegrationTest() {
    @Inject lateinit var cleaner: AccountDeletionCleaner

    @Inject lateinit var enqueueTask: EnqueueTask

    @Inject lateinit var pins: PinRepositoryInterface

    @Inject lateinit var boards: BoardRepositoryInterface

    @Inject lateinit var collections: RemoteCollectionRepositoryInterface

    @Inject lateinit var tags: TagRepositoryInterface

    @Inject lateinit var persons: PersonRepositoryInterface

    @Inject lateinit var media: MediaRepositoryInterface

    @Inject lateinit var frames: MediaFrameRepositoryInterface

    @Inject lateinit var downloads: MediaDownloadRepositoryInterface

    @Inject lateinit var duplicates: PinDuplicateRepositoryInterface

    @Inject lateinit var exports: UserDataExportRepositoryInterface

    @Inject lateinit var imports: UserDataImportRepositoryInterface

    @Inject lateinit var importIssues: UserDataImportIssueRepositoryInterface

    /** Every table but `tasks`, which holds no key to a user. */
    private fun userTables(): List<String> = tables() - "tasks"

    private fun rowsIn(table: String): Int? =
        DB.getDefault().sqlQuery("select count(*) as n from $table").findOne()?.getInteger("n")

    private fun emptyTables() = userTables().filter { rowsIn(it) == 0 }

    private fun url(text: String) = checkNotNull(HttpUrl.parse(text))

    private fun savePin(user: User, tag: Tag? = null, board: Board? = null, person: Person? = null): Pin =
        pins.savePin(
            Pin(
                id = randomUUID(),
                author = user,
                sourceContextUrl = null,
                sourceMediaUrl = null,
                description = "",
                tags = listOfNotNull(tag),
                boards = listOfNotNull(board),
                createdAt = AT,
                updatedAt = AT,
                publisher = person,
                creators = listOfNotNull(person),
            )
        )

    /** The rows a user's pin carries, its download's task returned. */
    private fun seedPinRows(user: User): UUID {
        val board = boards.saveBoard(Board(randomUUID(), user, boardName("board"), "", AT, AT))
        collections.saveRemoteCollection(
            RemoteCollection(randomUUID(), user, url("https://example.com/c"), collectionName("c"), board, AT)
        )
        val tag = tags.saveTag(Tag(randomUUID(), user, tagName("tag"), AT))
        val person = persons.savePerson(Person(randomUUID(), user, personName("Al"), emptySet(), AT))
        val pin = savePin(user, tag, board, person)
        val saved = media.save(Media.StillImage(randomUUID(), pin.id, "image/png", 1, 1, 1, "", "originals/x", AT))
        frames.save(saved.id, listOf(PdqHash(List(4) { 0L }, quality = 100)))
        duplicates.addMissing(pin.id, listOf(savePin(user).id))
        // Due in a day, so the worker never claims it during the case.
        val task = enqueueTask.enqueue(kind = "test", payload = "", maxAttempts = 1, delay = Duration.ofDays(1))
        downloads.upsertPending(pin.id, url("https://example.com/i.png"), task.id, AT)
        return task.id
    }

    private fun seedArchiveRows(user: User) {
        exports.save(
            UserDataExport(randomUUID(), user.id, UserDataExportState.PENDING, formatVersion = 1, requestedAt = AT)
        )
        val import = imports.save(UserDataImport(randomUUID(), user.id, UserDataImportState.COMPLETED, AT, AT))
        importIssues.save(
            UserDataImportIssue(
                id = randomUUID(),
                importId = import.id,
                kind = UserDataImportIssueKind.LINE_REJECTED,
                line = 1,
                subject = null,
                detail = "seeded",
            )
        )
    }

    @Test
    fun `Given one row of a user in every table, Then deleting the account leaves none and cancels its download`() {
        // Given: the user, its password hash and its session come from the sign-up and the sign-in
        val user = createAuthenticatedUser().user
        val downloadTask = seedPinRows(user)
        seedArchiveRows(user)
        val unseeded = emptyTables()

        // When
        cleaner.deleteAccountData(user.id)

        // Then
        assertEquals(listOf<String>() to userTables(), unseeded to emptyTables())
        assertEquals("CANCELLED", QTaskModel().id.equalTo(downloadTask).findOne()?.state)
    }

    private companion object {
        val AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
