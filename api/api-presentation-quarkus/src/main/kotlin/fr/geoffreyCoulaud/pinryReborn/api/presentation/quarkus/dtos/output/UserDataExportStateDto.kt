package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** Closed on the wire, and only what changes a client's behaviour: the cause of `GONE` is its `reasonCode`. */
enum class UserDataExportStateDto {
    PENDING,
    READY,
    FAILED,
    GONE,
}
