package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImport
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImportDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImportPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class UserDataImportGetterTest : BaseTest() {
    private val repository = mockk<UserDataImportRepositoryInterface>()
    private val getter = UserDataImportGetter(repository)
    private val user = User(id = randomUUID(), name = "alice", createdAt = TestTime.now)
    private val now = Instant.parse("2026-08-14T10:00:00Z")

    private fun importFor(userId: UUID) =
        UserDataImport(
            id = randomUUID(),
            userId = userId,
            state = UserDataImportState.RUNNING,
            requestedAt = now,
        )

    @Test
    fun `Given an unknown id, Then reading it is refused as absent`() {
        // Given
        val importId = randomUUID()
        every { repository.findById(importId) } returns null

        // When / Then
        assertThrows(ImportDoesNotExistError::class.java) { getter.get(user, importId) }
    }

    @Test
    fun `Given another user's import, Then reading it is refused`() {
        // Given
        val userDataImport = importFor(userId = randomUUID())
        every { repository.findById(userDataImport.id) } returns userDataImport

        // When / Then
        assertThrows(ImportPermissionError::class.java) { getter.get(user, userDataImport.id) }
    }

    @Test
    fun `Given the owner's import, Then it is returned`() {
        // Given
        val userDataImport = importFor(userId = user.id)
        every { repository.findById(userDataImport.id) } returns userDataImport

        // When
        val result = getter.get(user, userDataImport.id)

        // Then
        assertEquals(userDataImport, result)
    }

    @Test
    fun `Given a user's import history, Then the repository page is returned as-is`() {
        // Given
        val cursor = Cursor(pivotId = randomUUID(), direction = CursorDirection.FORWARD)
        val page = Page(items = listOf(importFor(userId = user.id)), previousCursor = null, nextCursor = null)
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
        val empty = Page<UserDataImport>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { repository.findAllForUser(user.id, null, any()) } returns empty

        // When
        getter.list(user, cursor = null, pageSize = 0)
        getter.list(user, cursor = null, pageSize = 10_000)

        // Then
        verify { repository.findAllForUser(user.id, null, 1) }
        verify { repository.findAllForUser(user.id, null, PinGetter.MAX_PAGE_SIZE) }
    }
}
