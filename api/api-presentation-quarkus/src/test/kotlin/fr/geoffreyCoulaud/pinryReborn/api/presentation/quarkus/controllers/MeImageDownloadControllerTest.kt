package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDirectionDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ImageDownloads
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.security.identity.SecurityIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class MeImageDownloadControllerTest {
    private val user = User(randomUUID(), "alice", createdAt = TestTime.now)
    private val identity = mockk<SecurityIdentity> { every { getAttribute<User>("user") } returns user }
    private val imageDownloads = mockk<ImageDownloads>(relaxed = true)
    private val pinId = randomUUID()

    private val controller = MeImageDownloadController(imageDownloads, identity)

    @Test
    fun `Given no cursor, Then the list asks for the first page at the default size`() {
        // Given
        every { imageDownloads.list(user, null, DEFAULT_PAGE_SIZE) } returns
            Page(
                items = listOf(
                    ImageDownload(pinId, "https://x/i.png", DownloadStatus.PENDING, null, null, randomUUID(),
                        Instant.EPOCH, Instant.EPOCH),
                ),
                previousCursor = null,
                nextCursor = null,
            )

        // When
        val dto = controller.listImageDownloads(cursorInput = null, pageSizeInput = null)

        // Then
        assertEquals(listOf(pinId), dto.downloads.map { it.pinId })
        verify { imageDownloads.list(user, null, DEFAULT_PAGE_SIZE) }
    }

    @Test
    fun `Given a cursor and a page size, Then both reach the use case`() {
        // Given
        val cursor = CursorDto(pivotId = randomUUID(), direction = CursorDirectionDto.FORWARD)
        val empty = Page<ImageDownload>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { imageDownloads.list(user, cursor.toDomain(), 5) } returns empty

        // When
        controller.listImageDownloads(cursorInput = cursor, pageSizeInput = 5)

        // Then
        verify { imageDownloads.list(user, cursor.toDomain(), 5) }
    }

    @Test
    fun `Given a pin id, Then the deletion is scoped to the caller and answers 204`() {
        // When
        val response = controller.deleteImageDownload(pinId)

        // Then
        assertEquals(NO_CONTENT, response.status)
        verify { imageDownloads.delete(user, pinId) }
    }

    private companion object {
        const val NO_CONTENT = 204
        const val DEFAULT_PAGE_SIZE = 20
    }
}
