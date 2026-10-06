package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A pin's original, its kind being its type; a rendition's cost is judged on its frames (ADR 0050, decision 2). */
sealed interface Media : Identifiable {
    val pinId: UUID
    val mimeType: String
    val width: Int
    val height: Int
    val byteSize: Long
    val contentHash: String
    val storageKey: String
    val createdAt: Instant
    val frames: Int
    val animated: Boolean
        get() = this !is StillImage

    data class StillImage(
        override val id: UUID,
        override val pinId: UUID,
        override val mimeType: String,
        override val width: Int,
        override val height: Int,
        override val byteSize: Long,
        override val contentHash: String,
        override val storageKey: String,
        override val createdAt: Instant,
    ) : Media {
        override val frames: Int
            get() = 1
    }

    data class AnimatedImage(
        override val id: UUID,
        override val pinId: UUID,
        override val mimeType: String,
        override val width: Int,
        override val height: Int,
        override val byteSize: Long,
        override val contentHash: String,
        override val storageKey: String,
        override val createdAt: Instant,
        override val frames: Int,
    ) : Media

    /** Its rates in bits per second. */
    data class Video(
        override val id: UUID,
        override val pinId: UUID,
        override val mimeType: String,
        override val width: Int,
        override val height: Int,
        override val byteSize: Long,
        override val contentHash: String,
        override val storageKey: String,
        override val createdAt: Instant,
        override val frames: Int,
        val duration: Duration,
        val videoBitRate: Long,
        val sound: Sound?,
    ) : Media

    /** A video's first audio track. */
    data class Sound(val channels: Int, val bitRate: Long)
}
