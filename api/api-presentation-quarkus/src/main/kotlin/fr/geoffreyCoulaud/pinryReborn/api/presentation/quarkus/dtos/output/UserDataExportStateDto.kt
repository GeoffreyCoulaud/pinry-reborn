package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** Closed on the wire: a client's behaviour hangs on it (`docs/specs/2026-09-25-the-data-travels.md`, decision I). */
enum class UserDataExportStateDto {
    PENDING,
    READY,
    FAILED,
    EXPIRED,
    DELETED,
    SUPERSEDED,
}
