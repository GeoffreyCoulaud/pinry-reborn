package fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions

open class PinUpdateError(message: String, code: ErrorCode, cause: Throwable? = null) :
    BaseError(message, code, cause)

class PinUpdatePinDoesNotExistError : PinUpdateError(
    "Pin does not exist", ErrorCode.PIN_DOES_NOT_EXIST
)

class PinUpdatePermissionError : PinUpdateError(
    "Insufficient permissions", ErrorCode.PIN_INSUFFICIENT_PERMISSIONS
)

class PinUpdateSoftDeletedPinError : PinUpdateError(
    "Cannot update a soft-deleted pin", ErrorCode.PIN_ALREADY_SOFT_DELETED
)
