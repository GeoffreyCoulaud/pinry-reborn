package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** Open: `ExtensibleEnumsFilter` publishes it as `x-extensible-enum`, so a value added breaks no client. */
enum class DownloadReasonDto {
    URL_NOT_ALLOWED,
    UNREACHABLE,
    ACCESS_DENIED,
    NOT_FOUND,
    TOO_LARGE,
    INVALID_IMAGE,
    TOO_MANY_PIXELS,
    INTERNAL_ERROR,
    FETCH_FAILED,
}
