package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Image
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinImageStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinImageStateMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageReplacement
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class PinImageStateMapperTest {
    private val pinId = randomUUID()

    @Test fun `Given READY, Then the dto carries the serve url and dimensions`() {
        val img =
            Image(randomUUID(), pinId, "image/png", 4, 5, false, 6, "h", "originals/x/$pinId/i.png", Instant.EPOCH)
        val dto = PinImageState(PinImageStatus.READY, img, null, null).toDto(pinId)
        assertEquals(PinImageStatusDto.READY, dto.status)
        assertEquals("/api/v1/pins/$pinId/image", dto.url)
        assertEquals(4, dto.width)
    }

    @Test fun `Given FAILED, Then the dto carries the reason code and a message`() {
        val dto = PinImageState(PinImageStatus.FAILED, null, DownloadReason.ACCESS_DENIED, null).toDto(pinId)
        assertEquals(PinImageStatusDto.FAILED, dto.status)
        assertEquals(DownloadReasonDto.ACCESS_DENIED, dto.reasonCode)
        assertTrue(dto.message!!.isNotBlank())
    }

    @Test fun `Given READY with a FAILED replacement, Then the replacement carries its reason`() {
        val img =
            Image(randomUUID(), pinId, "image/png", 1, 1, false, 1, "h", "originals/x/$pinId/i.png", Instant.EPOCH)
        val state =
            PinImageState(
                PinImageStatus.READY,
                img,
                null,
                PinImageReplacement(DownloadStatus.FAILED, DownloadReason.NOT_FOUND),
            )
        val dto = state.toDto(pinId)
        assertEquals(DownloadStatusDto.FAILED, dto.replacement?.status)
        assertEquals(DownloadReasonDto.NOT_FOUND, dto.replacement?.reasonCode)
    }

    @Test fun `Given NONE, Then the dto has no url, mimeType, dimensions, reason or replacement`() {
        val dto = PinImageState(PinImageStatus.NONE, null, null, null).toDto(pinId)
        assertEquals(PinImageStatusDto.NONE, dto.status)
        assertNull(dto.url)
        assertNull(dto.mimeType)
        assertNull(dto.width)
        assertNull(dto.height)
        assertNull(dto.byteSize)
        assertNull(dto.reasonCode)
        assertNull(dto.message)
        assertNull(dto.replacement)
    }

    @Test fun `Given READY with a successful replacement, Then the replacement has no reason or message`() {
        val img =
            Image(randomUUID(), pinId, "image/png", 1, 1, false, 1, "h", "originals/x/$pinId/i.png", Instant.EPOCH)
        val state =
            PinImageState(
                PinImageStatus.READY,
                img,
                null,
                PinImageReplacement(DownloadStatus.PENDING, null),
            )
        val dto = state.toDto(pinId)
        assertEquals(DownloadStatusDto.PENDING, dto.replacement?.status)
        assertNull(dto.replacement?.reasonCode)
        assertNull(dto.replacement?.message)
    }

    @Test fun `Given every PinImageStatus, Then the dto carries the value of the same name`() {
        for (status in PinImageStatus.entries) {
            val dto = PinImageState(status, null, null, null).toDto(pinId)
            assertEquals(status.name, dto.status.name)
        }
    }

    @Test fun `Given every DownloadStatus, Then the replacement carries the value of the same name`() {
        for (status in DownloadStatus.entries) {
            val state = PinImageState(PinImageStatus.READY, null, null, PinImageReplacement(status, null))
            assertEquals(status.name, state.toDto(pinId).replacement?.status?.name)
        }
    }

    @Test fun `Given every DownloadReason, Then the dto carries the reason of the same name and a message`() {
        for (reason in DownloadReason.entries) {
            val dto = PinImageState(PinImageStatus.FAILED, null, reason, null).toDto(pinId)
            assertEquals(reason.name, dto.reasonCode?.name)
            assertTrue(dto.message!!.isNotBlank()) { "expected a message for $reason" }
        }
    }
}
