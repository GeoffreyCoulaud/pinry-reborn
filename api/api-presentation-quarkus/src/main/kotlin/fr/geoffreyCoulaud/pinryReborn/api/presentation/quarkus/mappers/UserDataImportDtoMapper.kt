package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImport
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportFailure
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PaginationOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDto

// The issues have their own mapper object: both pages erase to one `toDto(Page)` JVM signature.
object UserDataImportDtoMapper {
    fun UserDataImport.toDto() = UserDataImportOutputDto(
        id = id,
        state = state.toDto(),
        requestedAt = requestedAt,
        uploadedBytes = uploadedBytes,
        byteSize = byteSize,
        archiveCompletedAt = archiveCompletedAt,
        startedAt = startedAt,
        completedAt = completedAt,
        formatVersion = formatVersion,
        announcedPins = announcedPins,
        processedPins = processedPins,
        createdPins = createdPins,
        skippedPins = skippedPins,
        createdBoards = createdBoards,
        skippedBoards = skippedBoards,
        createdTags = createdTags,
        skippedTags = skippedTags,
        issueCount = issueCount,
        issueDetailTruncated = issueDetailTruncated,
        reasonCode = failureCode?.toDto(),
    )

    fun Page<UserDataImport>.toDto() = UserDataImportListOutputDto(
        imports = items.map { it.toDto() },
        pagination = PaginationOutputDto(
            previousCursor = previousCursor?.toDto(),
            nextCursor = nextCursor?.toDto(),
        ),
    )

    private fun UserDataImportState.toDto(): UserDataImportStateDto =
        when (this) {
            UserDataImportState.AWAITING_ARCHIVE -> UserDataImportStateDto.AWAITING_ARCHIVE
            UserDataImportState.PENDING -> UserDataImportStateDto.PENDING
            UserDataImportState.RUNNING -> UserDataImportStateDto.RUNNING
            UserDataImportState.COMPLETED -> UserDataImportStateDto.COMPLETED
            UserDataImportState.FAILED -> UserDataImportStateDto.FAILED
            UserDataImportState.CANCELLED -> UserDataImportStateDto.CANCELLED
            UserDataImportState.ABANDONED -> UserDataImportStateDto.ABANDONED
        }

    private fun UserDataImportFailure.toDto(): UserDataImportReasonDto =
        when (this) {
            UserDataImportFailure.USER_GONE -> UserDataImportReasonDto.USER_GONE
            UserDataImportFailure.IMPORT_FAILED -> UserDataImportReasonDto.IMPORT_FAILED
            UserDataImportFailure.ARCHIVE_UNREADABLE -> UserDataImportReasonDto.ARCHIVE_UNREADABLE
            UserDataImportFailure.MANIFEST_MISSING -> UserDataImportReasonDto.MANIFEST_MISSING
            UserDataImportFailure.UNSUPPORTED_FORMAT_VERSION -> UserDataImportReasonDto.UNSUPPORTED_FORMAT_VERSION
            UserDataImportFailure.IMPORT_INTERRUPTED -> UserDataImportReasonDto.IMPORT_INTERRUPTED
        }
}
