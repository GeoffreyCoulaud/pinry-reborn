package fr.geoffreyCoulaud.pinryReborn.api.domain.enums

/** Why an import is FAILED. Stored by name, so renaming an entry needs a migration of the rows. */
enum class UserDataImportFailure {
    USER_GONE,
    IMPORT_FAILED,
    ARCHIVE_UNREADABLE,
    MANIFEST_MISSING,
    UNSUPPORTED_FORMAT_VERSION,
    IMPORT_INTERRUPTED,
}
