package fr.geoffreyCoulaud.pinryReborn.api.domain.enums

/** Why an export is FAILED. Stored by name, so renaming an entry needs a migration of the rows. */
enum class UserDataExportFailure {
    USER_GONE,
    DISK_FULL,
    BUILD_FAILED,
    EXPORT_INTERRUPTED,
}
