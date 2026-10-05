package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class Media(
    override val id: UUID,
    val pinId: UUID,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val animated: Boolean,
    val byteSize: Long,
    val contentHash: String,
    val storageKey: String,
    val createdAt: Instant,
    // A still image's values, which a rendition's cost is judged on (ADR 0050, decision 2).
    val frames: Int = 1,
    val duration: Duration? = null,
    // A video's alone, in bits per second.
    val videoBitRate: Long? = null,
    val audioChannels: Int? = null,
    val audioBitRate: Long? = null,
) : Identifiable {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
