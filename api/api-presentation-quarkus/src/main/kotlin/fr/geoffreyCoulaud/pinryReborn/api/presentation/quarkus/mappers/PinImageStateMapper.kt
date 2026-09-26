package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinImageStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinImageStateDto.ReplacementDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinImageStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageReplacement
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageStatus
import java.util.UUID

object PinImageStateMapper {
    fun PinImageState.toDto(pinId: UUID): PinImageStateDto {
        val img = image
        return PinImageStateDto(
            status = status.toDto(),
            url = img?.let { "/api/v1/pins/$pinId/image" },
            mimeType = img?.mimeType,
            width = img?.width,
            height = img?.height,
            byteSize = img?.byteSize,
            reasonCode = reasonCode?.toDto(),
            message = reasonCode?.let { messageFor(it) },
            replacement = replacement?.toDto(),
        )
    }

    private fun PinImageReplacement.toDto() =
        ReplacementDto(
            status = status.toDto(),
            reasonCode = reasonCode?.toDto(),
            message = reasonCode?.let { messageFor(it) },
        )

    private fun PinImageStatus.toDto(): PinImageStatusDto =
        when (this) {
            PinImageStatus.NONE -> PinImageStatusDto.NONE
            PinImageStatus.PENDING -> PinImageStatusDto.PENDING
            PinImageStatus.READY -> PinImageStatusDto.READY
            PinImageStatus.FAILED -> PinImageStatusDto.FAILED
        }

    internal fun DownloadStatus.toDto(): DownloadStatusDto =
        when (this) {
            DownloadStatus.PENDING -> DownloadStatusDto.PENDING
            DownloadStatus.FAILED -> DownloadStatusDto.FAILED
        }

    internal fun DownloadReason.toDto(): DownloadReasonDto =
        when (this) {
            DownloadReason.URL_NOT_ALLOWED -> DownloadReasonDto.URL_NOT_ALLOWED
            DownloadReason.UNREACHABLE -> DownloadReasonDto.UNREACHABLE
            DownloadReason.ACCESS_DENIED -> DownloadReasonDto.ACCESS_DENIED
            DownloadReason.NOT_FOUND -> DownloadReasonDto.NOT_FOUND
            DownloadReason.TOO_LARGE -> DownloadReasonDto.TOO_LARGE
            DownloadReason.INVALID_IMAGE -> DownloadReasonDto.INVALID_IMAGE
            DownloadReason.TOO_MANY_PIXELS -> DownloadReasonDto.TOO_MANY_PIXELS
            DownloadReason.INTERNAL_ERROR -> DownloadReasonDto.INTERNAL_ERROR
            DownloadReason.FETCH_FAILED -> DownloadReasonDto.FETCH_FAILED
        }

    // Shared with ImageDownloadDtoMapper: one reason, one sentence, declared once.
    internal fun messageFor(reason: DownloadReason): String =
        when (reason) {
            DownloadReason.URL_NOT_ALLOWED -> "This URL is not allowed."
            DownloadReason.UNREACHABLE -> "The server could not reach this URL."
            DownloadReason.ACCESS_DENIED -> "The site refused the server access. Upload the image directly."
            DownloadReason.NOT_FOUND -> "No image at this URL."
            DownloadReason.TOO_LARGE -> "Image too large."
            DownloadReason.INVALID_IMAGE -> "The content is not a supported image."
            DownloadReason.TOO_MANY_PIXELS -> "Dimensions too large."
            DownloadReason.INTERNAL_ERROR -> "Temporary error, try again later."
            DownloadReason.FETCH_FAILED -> "The download failed."
        }
}
