package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaDownloadModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.MediaDownloadModelMapper.toModel
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaDownloadModelMapperTest {
    @Test
    fun `Given a PENDING download, Then toModel and toDomain round-trip its fields`() {
        val download =
            MediaDownload(
                pinId = randomUUID(),
                sourceUrl = checkNotNull(HttpUrl.parse("https://x/i.png")),
                status = DownloadStatus.PENDING,
                reasonCode = null,
                lastError = null,
                taskId = randomUUID(),
                requestedAt = Instant.parse("2026-07-10T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-10T00:00:01Z"),
            )
        assertEquals(download, download.toModel(id = randomUUID()).toDomain())
    }

    @Test
    fun `Given a FAILED download with a reason, Then it round-trips the reason`() {
        val download =
            MediaDownload(
                pinId = randomUUID(),
                sourceUrl = checkNotNull(HttpUrl.parse("https://x/i.png")),
                status = DownloadStatus.FAILED,
                reasonCode = DownloadReason.ACCESS_DENIED,
                lastError = "403",
                taskId = randomUUID(),
                requestedAt = Instant.parse("2026-07-10T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-10T00:00:02Z"),
            )
        assertEquals(download, download.toModel(id = randomUUID()).toDomain())
    }
}
