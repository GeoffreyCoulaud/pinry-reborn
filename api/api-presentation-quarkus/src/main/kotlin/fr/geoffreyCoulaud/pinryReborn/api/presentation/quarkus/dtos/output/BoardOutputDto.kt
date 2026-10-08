package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import java.util.UUID

data class BoardOutputDto(
    val id: UUID,
    val name: String,
    val description: String,
    val pinCount: Int,
    /** The media of the board's newest active pin holding one, or null when no pin does. */
    val coverUrl: String?,
    /** The remote collections linked to the board, sorted by name with its case folded. */
    val remoteCollections: List<RemoteCollectionOutputDto>,
)
