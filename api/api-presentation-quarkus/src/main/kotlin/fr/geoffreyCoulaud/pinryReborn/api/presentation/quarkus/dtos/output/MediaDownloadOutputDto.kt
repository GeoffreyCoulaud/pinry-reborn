package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import java.time.Instant
import java.util.UUID
import org.eclipse.microprofile.openapi.annotations.media.Schema

/** One entry of the task centre: `reasonCode` with `message` is what a client acts on, as in [PinMediaStateDto]. */
data class MediaDownloadOutputDto(
    val pinId: UUID,
    @field:Schema(format = "uri") val sourceUrl: String,
    val status: DownloadStatusDto,
    val requestedAt: Instant,
    val updatedAt: Instant,
    val reasonCode: DownloadReasonDto?,
    val message: String?,
)
