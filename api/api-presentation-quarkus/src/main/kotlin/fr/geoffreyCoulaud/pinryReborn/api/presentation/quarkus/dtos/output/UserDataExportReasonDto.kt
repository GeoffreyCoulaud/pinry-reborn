package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** Open: `ExtensibleEnumsFilter` publishes it as `x-extensible-enum`, so a value added breaks no client. */
enum class UserDataExportReasonDto {
    USER_GONE,
    DISK_FULL,
    BUILD_FAILED,
    EXPORT_INTERRUPTED,
    EXPIRED,
    DELETED,
    SUPERSEDED,
}
