package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDirectionDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.usecases.MediaDownloads
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.security.identity.SecurityIdentity
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MeMediaDownloadControllerTest {
    private val user = User(randomUUID(), "alice", createdAt = TestTime.now)
    private val identity = mockk<SecurityIdentity> { every { getAttribute<User>("user") } returns user }
    private val mediaDownloads = mockk<MediaDownloads>(relaxed = true)
    private val pinId = randomUUID()

    private val controller = MeMediaDownloadController(mediaDownloads, identity)

    @Test
    fun `Given no cursor, Then the list asks for the first page at the default size`() {
        // Given
        every { mediaDownloads.list(user, null, DEFAULT_PAGE_SIZE) } returns
            Page(
                items =
                    listOf(
                        MediaDownload(
                            pinId,
                            checkNotNull(HttpUrl.parse("https://x/i.png")),
                            DownloadStatus.PENDING,
                            null,
                            null,
                            randomUUID(),
                            Instant.EPOCH,
                            Instant.EPOCH,
                        )
                    ),
                previousCursor = null,
                nextCursor = null,
            )

        // When
        val dto = controller.listMediaDownloads(cursorInput = null, pageSizeInput = null)

        // Then
        assertEquals(listOf(pinId), dto.downloads.map { it.pinId })
        verify { mediaDownloads.list(user, null, DEFAULT_PAGE_SIZE) }
    }

    @Test
    fun `Given a cursor and a page size, Then both reach the use case`() {
        // Given
        val cursor = CursorDto(pivotId = randomUUID(), direction = CursorDirectionDto.FORWARD)
        val empty = Page<MediaDownload>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { mediaDownloads.list(user, cursor.toDomain(), 5) } returns empty

        // When
        controller.listMediaDownloads(cursorInput = cursor, pageSizeInput = 5)

        // Then
        verify { mediaDownloads.list(user, cursor.toDomain(), 5) }
    }

    @Test
    fun `Given a pin id, Then the deletion is scoped to the caller and answers 204`() {
        // When
        val response = controller.deleteMediaDownload(pinId)

        // Then
        assertEquals(NO_CONTENT, response.status)
        verify { mediaDownloads.delete(user, pinId) }
    }

    private companion object {
        const val NO_CONTENT = 204
        const val DEFAULT_PAGE_SIZE = 20
    }
}
