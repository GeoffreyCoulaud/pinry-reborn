package fr.geoffreyCoulaud.pinryReborn.api.domain.enums

enum class DownloadReason {
    URL_NOT_ALLOWED,
    UNREACHABLE,
    ACCESS_DENIED,
    NOT_FOUND,
    TOO_LARGE,
    INVALID_MEDIA,
    TOO_MANY_PIXELS,
    INTERNAL_ERROR,
    FETCH_FAILED,
    TOO_LONG,
    UNSUPPORTED_CODEC,
}
