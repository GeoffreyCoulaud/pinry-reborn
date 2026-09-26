package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** Open: `ExtensibleEnumsFilter` publishes it as `x-extensible-enum`, so a value added breaks no client. */
enum class UserDataImportReasonDto {
    USER_GONE,
    IMPORT_FAILED,
    ARCHIVE_UNREADABLE,
    MANIFEST_MISSING,
    UNSUPPORTED_FORMAT_VERSION,
    IMPORT_INTERRUPTED,
}
