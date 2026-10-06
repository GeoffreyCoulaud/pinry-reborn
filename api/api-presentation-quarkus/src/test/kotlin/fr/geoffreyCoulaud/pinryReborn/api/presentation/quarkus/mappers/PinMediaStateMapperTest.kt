package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinMediaStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMediaStateMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaReplacement
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinMediaStateMapperTest {
    private val pinId = randomUUID()

    @Test
    fun `Given READY, Then the dto carries the serve url and dimensions`() {
        val img =
            Media.StillImage(randomUUID(), pinId, "image/png", 4, 5, 6, "h", "originals/x/$pinId/i.png", Instant.EPOCH)
        val dto = PinMediaState(PinMediaStatus.READY, img, null, null).toDto(pinId)
        assertEquals(PinMediaStatusDto.READY, dto.status)
        assertEquals("/api/v1/pins/$pinId/media", dto.url)
        assertEquals(4, dto.width)
    }

    private fun video(sound: Media.Sound?) =
        Media.Video(
            randomUUID(),
            pinId,
            "video/mp4",
            4,
            5,
            6,
            "h",
            "originals/x/$pinId/v.mp4",
            Instant.EPOCH,
            frames = 30,
            duration = Duration.ofMillis(1_500),
            videoBitRate = 4_200_000,
            sound = sound,
        )

    @Test
    fun `Given a READY video, Then the dto carries its duration, rates and channels`() {
        val dto = PinMediaState(PinMediaStatus.READY, video(Media.Sound(2, 128_000)), null, null).toDto(pinId)
        assertEquals(1_500L, dto.durationMillis)
        assertEquals(4_200_000L, dto.videoBitRate)
        assertEquals(2, dto.audioChannels)
        assertEquals(128_000L, dto.audioBitRate)
    }

    @Test
    fun `Given a READY video without sound, Then the dto carries its video rate and no audio`() {
        val dto = PinMediaState(PinMediaStatus.READY, video(sound = null), null, null).toDto(pinId)
        assertEquals(4_200_000L, dto.videoBitRate)
        assertEquals(null to null, dto.audioChannels to dto.audioBitRate)
    }

    @Test
    fun `Given FAILED, Then the dto carries the reason code and a message`() {
        val dto = PinMediaState(PinMediaStatus.FAILED, null, DownloadReason.ACCESS_DENIED, null).toDto(pinId)
        assertEquals(PinMediaStatusDto.FAILED, dto.status)
        assertEquals(DownloadReasonDto.ACCESS_DENIED, dto.reasonCode)
        assertTrue(dto.message!!.isNotBlank())
    }

    @Test
    fun `Given READY with a FAILED replacement, Then the replacement carries its reason`() {
        val img =
            Media.StillImage(randomUUID(), pinId, "image/png", 1, 1, 1, "h", "originals/x/$pinId/i.png", Instant.EPOCH)
        val state =
            PinMediaState(
                PinMediaStatus.READY,
                img,
                null,
                PinMediaReplacement(DownloadStatus.FAILED, DownloadReason.NOT_FOUND),
            )
        val dto = state.toDto(pinId)
        assertEquals(DownloadStatusDto.FAILED, dto.replacement?.status)
        assertEquals(DownloadReasonDto.NOT_FOUND, dto.replacement?.reasonCode)
    }

    @Test
    fun `Given NONE, Then the dto has no url, mimeType, dimensions, reason or replacement`() {
        val dto = PinMediaState(PinMediaStatus.NONE, null, null, null).toDto(pinId)
        assertEquals(PinMediaStatusDto.NONE, dto.status)
        assertNull(dto.url)
        assertNull(dto.mimeType)
        assertNull(dto.width)
        assertNull(dto.height)
        assertNull(dto.byteSize)
        assertNull(dto.durationMillis)
        assertNull(dto.videoBitRate)
        assertNull(dto.audioChannels)
        assertNull(dto.audioBitRate)
        assertNull(dto.reasonCode)
        assertNull(dto.message)
        assertNull(dto.replacement)
    }

    @Test
    fun `Given READY with a successful replacement, Then the replacement has no reason or message`() {
        val img =
            Media.StillImage(randomUUID(), pinId, "image/png", 1, 1, 1, "h", "originals/x/$pinId/i.png", Instant.EPOCH)
        val state =
            PinMediaState(
                PinMediaStatus.READY,
                img,
                null,
                PinMediaReplacement(DownloadStatus.PENDING, null),
            )
        val dto = state.toDto(pinId)
        assertEquals(DownloadStatusDto.PENDING, dto.replacement?.status)
        assertNull(dto.replacement?.reasonCode)
        assertNull(dto.replacement?.message)
    }

    @Test
    fun `Given every PinMediaStatus, Then the dto carries the value of the same name`() {
        for (status in PinMediaStatus.entries) {
            val dto = PinMediaState(status, null, null, null).toDto(pinId)
            assertEquals(status.name, dto.status.name)
        }
    }

    @Test
    fun `Given every DownloadStatus, Then the replacement carries the value of the same name`() {
        for (status in DownloadStatus.entries) {
            val state = PinMediaState(PinMediaStatus.READY, null, null, PinMediaReplacement(status, null))
            assertEquals(status.name, state.toDto(pinId).replacement?.status?.name)
        }
    }

    @Test
    fun `Given every DownloadReason, Then the dto carries the reason of the same name and a message`() {
        for (reason in DownloadReason.entries) {
            val dto = PinMediaState(PinMediaStatus.FAILED, null, reason, null).toDto(pinId)
            assertEquals(reason.name, dto.reasonCode?.name)
            assertTrue(dto.message!!.isNotBlank()) { "expected a message for $reason" }
        }
    }
}
