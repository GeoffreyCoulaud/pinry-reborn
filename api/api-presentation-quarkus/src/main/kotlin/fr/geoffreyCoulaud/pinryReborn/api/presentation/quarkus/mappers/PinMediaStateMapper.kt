package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinMediaStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinMediaStateDto.ReplacementDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinMediaStatusDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaReplacement
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaStatus
import java.util.UUID

object PinMediaStateMapper {
    fun PinMediaState.toDto(pinId: UUID): PinMediaStateDto {
        val img = media
        return PinMediaStateDto(
            status = status.toDto(),
            url = img?.let { mediaUrl(pinId) },
            mimeType = img?.mimeType,
            width = img?.width,
            height = img?.height,
            byteSize = img?.byteSize,
            durationMillis = img?.duration?.toMillis(),
            videoBitRate = img?.videoBitRate,
            audioChannels = img?.audioChannels,
            audioBitRate = img?.audioBitRate,
            reasonCode = reasonCode?.toDto(),
            message = reasonCode?.let { messageFor(it) },
            replacement = replacement?.toDto(),
        )
    }

    // Shared with BoardMapper: a board's cover is its pin's media, at the same address.
    internal fun mediaUrl(pinId: UUID): String = "/api/v1/pins/$pinId/media"

    private fun PinMediaReplacement.toDto() =
        ReplacementDto(
            status = status.toDto(),
            reasonCode = reasonCode?.toDto(),
            message = reasonCode?.let { messageFor(it) },
        )

    private fun PinMediaStatus.toDto(): PinMediaStatusDto =
        when (this) {
            PinMediaStatus.NONE -> PinMediaStatusDto.NONE
            PinMediaStatus.PENDING -> PinMediaStatusDto.PENDING
            PinMediaStatus.READY -> PinMediaStatusDto.READY
            PinMediaStatus.FAILED -> PinMediaStatusDto.FAILED
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
            DownloadReason.INVALID_MEDIA -> DownloadReasonDto.INVALID_MEDIA
            DownloadReason.TOO_MANY_PIXELS -> DownloadReasonDto.TOO_MANY_PIXELS
            DownloadReason.INTERNAL_ERROR -> DownloadReasonDto.INTERNAL_ERROR
            DownloadReason.FETCH_FAILED -> DownloadReasonDto.FETCH_FAILED
            DownloadReason.TOO_LONG -> DownloadReasonDto.TOO_LONG
            DownloadReason.UNSUPPORTED_CODEC -> DownloadReasonDto.UNSUPPORTED_CODEC
            DownloadReason.NO_MEDIA_FOUND -> DownloadReasonDto.NO_MEDIA_FOUND
        }

    // Shared with MediaDownloadDtoMapper: one reason, one sentence, declared once.
    internal fun messageFor(reason: DownloadReason): String =
        when (reason) {
            DownloadReason.URL_NOT_ALLOWED -> "This URL is not allowed."
            DownloadReason.UNREACHABLE -> "The server could not reach this URL."
            DownloadReason.ACCESS_DENIED -> "The site refused the server access. Upload the media directly."
            DownloadReason.NOT_FOUND -> "No media at this URL."
            DownloadReason.TOO_LARGE -> "Media too large."
            DownloadReason.INVALID_MEDIA -> "The content is not a supported image or video."
            DownloadReason.TOO_MANY_PIXELS -> "Dimensions too large."
            DownloadReason.INTERNAL_ERROR -> "Temporary error, try again later."
            DownloadReason.FETCH_FAILED -> "The download failed."
            DownloadReason.TOO_LONG -> "Video too long."
            DownloadReason.UNSUPPORTED_CODEC -> "The format or codec is not supported."
            DownloadReason.NO_MEDIA_FOUND -> "No media found on this page."
        }
}
