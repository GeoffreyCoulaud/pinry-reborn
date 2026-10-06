package fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions

open class PinDeletionError(
    message: String,
    code: ErrorCode,
    cause: Throwable? = null,
) : BaseError(message, code, cause)

class PinDeletionPinDoesNotExistError :
    PinDeletionError(
        "Pin does not exist",
        ErrorCode.PIN_DOES_NOT_EXIST,
    )

class PinDeletionPermissionError :
    PinDeletionError(
        "Insufficient permissions",
        ErrorCode.PIN_INSUFFICIENT_PERMISSIONS,
    )

class PinDeletionPinNotSoftDeletedError :
    PinDeletionError(
        "Pin is not soft-deleted",
        ErrorCode.PIN_NOT_SOFT_DELETED,
    )

class PinDeletionPinAlreadySoftDeletedError :
    PinDeletionError(
        "Pin is already soft-deleted",
        ErrorCode.PIN_ALREADY_SOFT_DELETED,
    )
