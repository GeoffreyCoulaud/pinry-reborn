package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID

class PinMediaStateTest {
    private val pinId = randomUUID()
    private fun media() =
        Media.StillImage(randomUUID(), pinId, "image/png", 1, 1, 1,"h", "originals/x/$pinId/i.png", Instant.EPOCH)
    private fun download(status: DownloadStatus, reason: DownloadReason? = null) =
        MediaDownload(pinId, "https://x", status, reason, null, randomUUID(), Instant.EPOCH, Instant.EPOCH)

    @Test fun `Given no image and no download, Then NONE`() {
        assertEquals(PinMediaStatus.NONE, PinMediaState.derive(null, null).status)
    }
    @Test fun `Given no image and a PENDING download, Then PENDING`() {
        assertEquals(PinMediaStatus.PENDING, PinMediaState.derive(null, download(DownloadStatus.PENDING)).status)
    }
    @Test fun `Given no image and a FAILED download, Then FAILED with the reason`() {
        val s = PinMediaState.derive(null, download(DownloadStatus.FAILED, DownloadReason.ACCESS_DENIED))
        assertEquals(PinMediaStatus.FAILED, s.status)
        assertEquals(DownloadReason.ACCESS_DENIED, s.reasonCode)
    }
    @Test fun `Given an image and no download, Then READY with no replacement`() {
        val s = PinMediaState.derive(media(), null)
        assertEquals(PinMediaStatus.READY, s.status)
        assertNull(s.replacement)
    }
    @Test fun `Given an image and a PENDING download, Then READY with a PENDING replacement`() {
        val s = PinMediaState.derive(media(), download(DownloadStatus.PENDING))
        assertEquals(PinMediaStatus.READY, s.status)
        assertEquals(DownloadStatus.PENDING, s.replacement?.status)
    }
    @Test fun `Given an image and a FAILED download, Then READY with a FAILED replacement carrying the reason`() {
        val s = PinMediaState.derive(media(), download(DownloadStatus.FAILED, DownloadReason.ACCESS_DENIED))
        assertEquals(PinMediaStatus.READY, s.status)
        assertEquals(DownloadStatus.FAILED, s.replacement?.status)
        assertEquals(DownloadReason.ACCESS_DENIED, s.replacement?.reasonCode)
    }
}
