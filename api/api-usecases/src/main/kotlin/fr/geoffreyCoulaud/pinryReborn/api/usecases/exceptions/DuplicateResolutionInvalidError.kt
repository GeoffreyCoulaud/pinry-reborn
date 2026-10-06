package fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions

/** Not exactly one pin kept, the open pin unnamed or rejected, or no other pin named. */
class DuplicateResolutionInvalidError :
    BaseError(
        "Name the open pin and one other, keep exactly one, and do not reject the open pin",
        ErrorCode.DUPLICATE_RESOLUTION_INVALID,
    )
