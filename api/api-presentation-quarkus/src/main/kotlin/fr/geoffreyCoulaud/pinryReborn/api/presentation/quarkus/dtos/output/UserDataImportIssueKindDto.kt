package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** Open: `ExtensibleEnumsFilter` publishes it as `x-extensible-enum`, so a value added breaks no client. */
enum class UserDataImportIssueKindDto {
    PIN_HAS_NO_MEDIA,
    MEDIA_ENTRY_MISSING,
    MEDIA_UNREADABLE,
    MEDIA_TOO_LARGE,
    MEDIA_TOO_MANY_PIXELS,
    MEDIA_AMBIGUOUS,
    MEDIA_DIGEST_MISMATCH,
    LINE_MALFORMED,
    FIELD_INVALID,
    ENTRY_PATH_INVALID,
    NAME_TAKEN_BY_RECYCLED,
    LINE_REJECTED,
}
