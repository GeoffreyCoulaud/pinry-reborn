package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.ImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.CursorDirection
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ImageDownloadDtoMapper.toDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class ImageDownloadDtoMapperTest {
    private val pinId = randomUUID()

    private fun download(status: DownloadStatus, reason: DownloadReason?) = ImageDownload(
        pinId = pinId,
        sourceUrl = "https://x/i.png",
        status = status,
        reasonCode = reason,
        lastError = "a transient error nobody outside the server reads",
        taskId = randomUUID(),
        requestedAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `Given a failed download, Then the dto carries its reason code and a message`() {
        // Given / When
        val dto = download(DownloadStatus.FAILED, DownloadReason.ACCESS_DENIED).toDto()

        // Then
        assertEquals(DownloadStatusDto.FAILED, dto.status)
        assertEquals(DownloadReasonDto.ACCESS_DENIED, dto.reasonCode)
        assertTrue(dto.message!!.isNotBlank())
    }

    @Test
    fun `Given a running download, Then the dto carries the source url and no reason`() {
        // Given / When
        val dto = download(DownloadStatus.PENDING, null).toDto()

        // Then
        assertEquals(DownloadStatusDto.PENDING, dto.status)
        assertEquals(pinId, dto.pinId)
        assertEquals("https://x/i.png", dto.sourceUrl)
        assertNull(dto.reasonCode)
        assertNull(dto.message)
    }

    @Test
    fun `Given a page of downloads, Then the dto holds one item per row and both cursors`() {
        // Given
        val next = Cursor(pivotId = randomUUID(), direction = CursorDirection.FORWARD)
        val page = Page(
            items = listOf(download(DownloadStatus.PENDING, null)),
            previousCursor = null,
            nextCursor = next,
        )

        // When
        val dto = page.toDto()

        // Then
        assertEquals(listOf(pinId), dto.downloads.map { it.pinId })
        assertNull(dto.pagination.previousCursor)
        assertEquals(next.pivotId, dto.pagination.nextCursor?.pivotId)
    }

    @Test
    fun `Given a last page, Then the dto carries the cursor back and none forward`() {
        // Given: the mirror of the case above, so neither cursor is mapped on one branch alone
        val previous = Cursor(pivotId = randomUUID(), direction = CursorDirection.BACKWARD)
        val page = Page(
            items = listOf(download(DownloadStatus.FAILED, DownloadReason.NOT_FOUND)),
            previousCursor = previous,
            nextCursor = null,
        )

        // When
        val dto = page.toDto()

        // Then
        assertEquals(previous.pivotId, dto.pagination.previousCursor?.pivotId)
        assertNull(dto.pagination.nextCursor)
    }
}
