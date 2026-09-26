package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImportIssue
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportIssueKind
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PaginationOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportIssueKindDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportIssueListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportIssueOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDto

object UserDataImportIssueDtoMapper {
    fun UserDataImportIssue.toDto() = UserDataImportIssueOutputDto(
        id = id,
        kind = kind.toDto(),
        line = line,
        subject = subject,
        detail = detail,
    )

    private fun UserDataImportIssueKind.toDto(): UserDataImportIssueKindDto =
        when (this) {
            UserDataImportIssueKind.PIN_HAS_NO_MEDIA -> UserDataImportIssueKindDto.PIN_HAS_NO_MEDIA
            UserDataImportIssueKind.MEDIA_ENTRY_MISSING -> UserDataImportIssueKindDto.MEDIA_ENTRY_MISSING
            UserDataImportIssueKind.MEDIA_UNREADABLE -> UserDataImportIssueKindDto.MEDIA_UNREADABLE
            UserDataImportIssueKind.MEDIA_TOO_LARGE -> UserDataImportIssueKindDto.MEDIA_TOO_LARGE
            UserDataImportIssueKind.MEDIA_TOO_MANY_PIXELS -> UserDataImportIssueKindDto.MEDIA_TOO_MANY_PIXELS
            UserDataImportIssueKind.MEDIA_AMBIGUOUS -> UserDataImportIssueKindDto.MEDIA_AMBIGUOUS
            UserDataImportIssueKind.MEDIA_DIGEST_MISMATCH -> UserDataImportIssueKindDto.MEDIA_DIGEST_MISMATCH
            UserDataImportIssueKind.LINE_MALFORMED -> UserDataImportIssueKindDto.LINE_MALFORMED
            UserDataImportIssueKind.FIELD_INVALID -> UserDataImportIssueKindDto.FIELD_INVALID
            UserDataImportIssueKind.ENTRY_PATH_INVALID -> UserDataImportIssueKindDto.ENTRY_PATH_INVALID
            UserDataImportIssueKind.NAME_TAKEN_BY_RECYCLED -> UserDataImportIssueKindDto.NAME_TAKEN_BY_RECYCLED
            UserDataImportIssueKind.LINE_REJECTED -> UserDataImportIssueKindDto.LINE_REJECTED
        }

    fun Page<UserDataImportIssue>.toDto() = UserDataImportIssueListOutputDto(
        issues = items.map { it.toDto() },
        pagination = PaginationOutputDto(
            previousCursor = previousCursor?.toDto(),
            nextCursor = nextCursor?.toDto(),
        ),
    )
}
