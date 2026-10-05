package fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions

/** No pair of the two pins, or one a recycled pin hides. */
class DuplicateDoesNotExistError : BaseError("Duplicate does not exist", ErrorCode.DUPLICATE_DOES_NOT_EXIST)
