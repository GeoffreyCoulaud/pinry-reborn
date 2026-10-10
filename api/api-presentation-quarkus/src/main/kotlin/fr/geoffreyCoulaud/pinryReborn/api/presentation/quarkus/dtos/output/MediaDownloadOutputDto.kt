package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import java.time.Instant
import java.util.UUID
import org.eclipse.microprofile.openapi.annotations.media.Schema

/**
 * One entry of the task centre. `lastError` stays out: it is the worker's own transient note, and `reasonCode` with
 * `message` is what a client acts on, exactly as in [PinMediaStateDto].
 */
data class MediaDownloadOutputDto(
    val pinId: UUID,
    @field:Schema(format = "uri") val sourceUrl: String,
    val status: DownloadStatusDto,
    val requestedAt: Instant,
    val updatedAt: Instant,
    val reasonCode: DownloadReasonDto?,
    val message: String?,
)
