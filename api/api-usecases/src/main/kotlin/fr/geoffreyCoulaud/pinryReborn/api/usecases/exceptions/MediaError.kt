package fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions

open class MediaError(message: String, code: ErrorCode, cause: Throwable? = null) : BaseError(message, code, cause)

class MediaPinDoesNotExistError : MediaError("Pin does not exist", ErrorCode.MEDIA_DOES_NOT_EXIST)

class MediaPermissionError : MediaError("Insufficient permissions", ErrorCode.MEDIA_INSUFFICIENT_PERMISSIONS)

class MediaDoesNotExistError : MediaError("Pin has no image", ErrorCode.MEDIA_DOES_NOT_EXIST)

class MediaTooLargeError(cause: Throwable? = null) :
    MediaError("Image exceeds the maximum size", ErrorCode.MEDIA_TOO_LARGE, cause)

class MediaInvalidError(message: String, cause: Throwable? = null) : MediaError(message, ErrorCode.MEDIA_INVALID, cause)

class MediaSourceUrlInvalidError(cause: Throwable? = null) :
    MediaError("Invalid source URL", ErrorCode.MEDIA_SOURCE_URL_INVALID, cause)

// The image family's 404, as for a pin nobody can reach: the message names the case, the code names
// the family, and a requester learns nothing about another account's rows.
class MediaDownloadDoesNotExistError : MediaError("Pin has no image download", ErrorCode.MEDIA_DOES_NOT_EXIST)

class MediaDownloadInProgressError :
    MediaError("The download is still running", ErrorCode.MEDIA_DOWNLOAD_IN_PROGRESS)
