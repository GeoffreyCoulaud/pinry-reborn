package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataExport
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportFailure
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportState
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.UserDataExportDtoMapper.toDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class UserDataExportDtoMapperTest {
    private fun pendingExport() = UserDataExport(
        id = randomUUID(),
        userId = randomUUID(),
        state = UserDataExportState.PENDING,
        formatVersion = 1,
        requestedAt = Instant.parse("2026-07-22T10:00:00Z"),
    )

    private fun readyExport() = pendingExport().copy(
        state = UserDataExportState.READY,
        completedAt = Instant.parse("2026-07-22T10:05:00Z"),
        expiresAt = Instant.parse("2026-07-29T10:05:00Z"),
        byteSize = 4096L,
        sha256 = "abcd",
        mediaType = "application/zip",
        fileExtension = "zip",
    )

    @Test
    fun `Given a pending export, Then toDto carries the state and leaves READY fields null`() {
        // Given
        val export = pendingExport()

        // When
        val dto = export.toDto()

        // Then
        assertEquals(UserDataExportStateDto.PENDING, dto.state)
        assertEquals(export.id, dto.id)
        assertEquals(export.formatVersion, dto.formatVersion)
        assertNull(dto.completedAt)
        assertNull(dto.byteSize)
        assertNull(dto.mediaType)
        assertNull(dto.sha256)
        assertNull(dto.reasonCode)
    }

    @Test
    fun `Given a ready export, Then toDto carries every field`() {
        // Given
        val export = readyExport()

        // When
        val dto = export.toDto()

        // Then
        assertEquals(UserDataExportStateDto.READY, dto.state)
        assertEquals(export.completedAt, dto.completedAt)
        assertEquals(export.expiresAt, dto.expiresAt)
        assertEquals(export.byteSize, dto.byteSize)
        assertEquals(export.mediaType, dto.mediaType)
        assertEquals(export.sha256, dto.sha256)
    }

    @Test
    fun `Given each live state, Then toDto carries the value of the same name and no reason`() {
        for (state in listOf(UserDataExportState.PENDING, UserDataExportState.READY)) {
            // Given
            val export = pendingExport().copy(state = state)

            // When
            val dto = export.toDto()

            // Then
            assertEquals(state.name, dto.state.name)
            assertNull(dto.reasonCode)
        }
    }

    @Test
    fun `Given each gone state, Then toDto says GONE and carries the state's name as its reason`() {
        for (state in UserDataExportState.entries.filter { it.isGone }) {
            // Given
            val export = pendingExport().copy(state = state)

            // When
            val dto = export.toDto()

            // Then
            assertEquals(UserDataExportStateDto.GONE, dto.state)
            assertEquals(state.name, dto.reasonCode?.name)
        }
    }

    @Test
    fun `Given each failure, Then toDto says FAILED and carries the failure's name as its reason`() {
        for (failure in UserDataExportFailure.entries) {
            // Given
            val export = pendingExport().copy(state = UserDataExportState.FAILED, failureCode = failure)

            // When
            val dto = export.toDto()

            // Then
            assertEquals(UserDataExportStateDto.FAILED, dto.state)
            assertEquals(failure.name, dto.reasonCode?.name)
        }
    }

    @Test
    fun `Given a failed export that recorded no failure, Then toDto carries no reason`() {
        // Given
        val export = pendingExport().copy(state = UserDataExportState.FAILED)

        // When
        val dto = export.toDto()

        // Then
        assertNull(dto.reasonCode)
    }

    @Test
    fun `Given a page with no previous and no next cursor, Then toDto maps both cursors to null`() {
        // Given
        val page = Page<UserDataExport>(items = listOf(pendingExport()), previousCursor = null, nextCursor = null)

        // When
        val result = page.toDto()

        // Then
        assertEquals(1, result.exports.size)
        assertNull(result.pagination.previousCursor)
        assertNull(result.pagination.nextCursor)
    }

    @Test
    fun `Given a page with a previous and a next cursor, Then toDto maps both cursors`() {
        // Given
        val previousCursor = Cursor(pivotId = randomUUID(), direction = CursorDirection.BACKWARD)
        val nextCursor = Cursor(pivotId = randomUUID(), direction = CursorDirection.FORWARD)
        val page = Page<UserDataExport>(
            items = listOf(pendingExport()),
            previousCursor = previousCursor,
            nextCursor = nextCursor,
        )

        // When
        val result = page.toDto()

        // Then
        assertNotNull(result.pagination.previousCursor)
        assertNotNull(result.pagination.nextCursor)
    }
}
