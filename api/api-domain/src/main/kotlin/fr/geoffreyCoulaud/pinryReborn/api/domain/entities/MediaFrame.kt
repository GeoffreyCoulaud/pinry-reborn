package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.util.UUID

/** A frame hash a media holds, as stored: the four words of a PDQ hash, whose quality is not kept. */
data class MediaFrame(val mediaId: UUID, val words: List<Long>)
