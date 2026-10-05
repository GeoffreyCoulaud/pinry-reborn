package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/** A likely duplicate of the path's pin: the other pin whole, so a client shows one it has not loaded. */
data class PinDuplicateOutputDto(
    val pin: PinOutputDto,
    /** The user said it is not a duplicate; it stays listed so the user can take that back. */
    val rejected: Boolean,
)
