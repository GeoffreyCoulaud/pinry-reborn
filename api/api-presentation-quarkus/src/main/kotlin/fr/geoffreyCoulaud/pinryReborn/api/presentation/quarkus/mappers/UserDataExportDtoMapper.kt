package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataExport
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportFailure
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportState
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PaginationOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDto

object UserDataExportDtoMapper {
    fun UserDataExport.toDto() = UserDataExportOutputDto(
        id = id,
        state = state.toDto(),
        requestedAt = requestedAt,
        completedAt = completedAt,
        expiresAt = expiresAt,
        byteSize = byteSize,
        mediaType = mediaType,
        sha256 = sha256,
        reasonCode = reason(),
        formatVersion = formatVersion,
    )

    fun Page<UserDataExport>.toDto() = UserDataExportListOutputDto(
        exports = items.map { it.toDto() },
        pagination = PaginationOutputDto(
            previousCursor = previousCursor?.toDto(),
            nextCursor = nextCursor?.toDto(),
        ),
    )

    private fun UserDataExportState.toDto(): UserDataExportStateDto =
        when (this) {
            UserDataExportState.PENDING -> UserDataExportStateDto.PENDING
            UserDataExportState.READY -> UserDataExportStateDto.READY
            UserDataExportState.FAILED -> UserDataExportStateDto.FAILED
            UserDataExportState.EXPIRED,
            UserDataExportState.DELETED,
            UserDataExportState.SUPERSEDED,
            -> UserDataExportStateDto.GONE
        }

    private fun UserDataExport.reason(): UserDataExportReasonDto? =
        when (state) {
            UserDataExportState.PENDING, UserDataExportState.READY -> null
            UserDataExportState.FAILED -> failureCode?.toDto()
            UserDataExportState.EXPIRED -> UserDataExportReasonDto.EXPIRED
            UserDataExportState.DELETED -> UserDataExportReasonDto.DELETED
            UserDataExportState.SUPERSEDED -> UserDataExportReasonDto.SUPERSEDED
        }

    private fun UserDataExportFailure.toDto(): UserDataExportReasonDto =
        when (this) {
            UserDataExportFailure.USER_GONE -> UserDataExportReasonDto.USER_GONE
            UserDataExportFailure.DISK_FULL -> UserDataExportReasonDto.DISK_FULL
            UserDataExportFailure.BUILD_FAILED -> UserDataExportReasonDto.BUILD_FAILED
            UserDataExportFailure.EXPORT_INTERRUPTED -> UserDataExportReasonDto.EXPORT_INTERRUPTED
        }
}
