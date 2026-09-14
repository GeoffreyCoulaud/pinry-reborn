package fr.geoffreyCoulaud.pinryReborn.api.usecases.exports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataExport
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataExportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ExportDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ExportPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UserDataExportGetterTest : BaseTest() {
    private val repository = mockk<UserDataExportRepositoryInterface>()
    private val getter = UserDataExportGetter(repository)
    private val user = User(id = randomUUID(), name = "alice", createdAt = TestTime.now)
    private val now = Instant.parse("2026-07-22T10:00:00Z")

    private fun exportFor(userId: UUID, state: UserDataExportState = UserDataExportState.PENDING) =
        UserDataExport(
            id = randomUUID(), userId = userId, state = state, formatVersion = 1, requestedAt = now,
        )

    @Test
    fun `Given an unknown id, Then getting it throws ExportDoesNotExistError`() {
        // Given
        val exportId = randomUUID()
        every { repository.findById(exportId) } returns null

        // When / Then
        assertThrows(ExportDoesNotExistError::class.java) { getter.get(user, exportId) }
    }

    @Test
    fun `Given another user's export, Then getting it throws ExportPermissionError`() {
        // Given
        val export = exportFor(userId = randomUUID())
        every { repository.findById(export.id) } returns export

        // When / Then
        assertThrows(ExportPermissionError::class.java) { getter.get(user, export.id) }
    }

    @Test
    fun `Given the owner's export, Then it is returned`() {
        // Given
        val export = exportFor(userId = user.id)
        every { repository.findById(export.id) } returns export

        // When
        val result = getter.get(user, export.id)

        // Then
        assertEquals(export, result)
    }

    @Test
    fun `Given a user's export history, Then the repository page is returned as-is`() {
        // Given
        val cursor = Cursor(pivotId = randomUUID(), direction = CursorDirection.FORWARD)
        val page = Page(items = listOf(exportFor(userId = user.id)), previousCursor = null, nextCursor = null)
        every { repository.findAllForUser(user.id, cursor, 20) } returns page

        // When
        val result = getter.list(user, cursor, 20)

        // Then
        assertEquals(page, result)
    }

    @Test
    fun `Given a page size outside the bounds, Then list clamps it before it reaches the store`() {
        // Given: at zero the pagination helper answers an empty page with no cursor at all, which a
        // client honouring the contract can never advance past
        val empty = Page<UserDataExport>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { repository.findAllForUser(user.id, null, any()) } returns empty

        // When
        getter.list(user, cursor = null, pageSize = 0)
        getter.list(user, cursor = null, pageSize = 10_000)

        // Then
        verify { repository.findAllForUser(user.id, null, 1) }
        verify { repository.findAllForUser(user.id, null, PinGetter.MAX_PAGE_SIZE) }
    }
}
