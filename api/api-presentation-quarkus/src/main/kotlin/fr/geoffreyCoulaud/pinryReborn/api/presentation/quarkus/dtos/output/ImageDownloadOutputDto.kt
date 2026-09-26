package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import java.time.Instant
import java.util.UUID

/**
 * One entry of the task centre. `lastError` stays out: it is the worker's own transient note, and
 * `reasonCode` with `message` is what a client acts on, exactly as in [PinImageStateDto].
 */
data class ImageDownloadOutputDto(
    val pinId: UUID,
    val sourceUrl: String,
    val status: DownloadStatusDto,
    val requestedAt: Instant,
    val updatedAt: Instant,
    val reasonCode: DownloadReasonDto?,
    val message: String?,
)
