package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.MediaModel
import java.time.Duration

object MediaModelMapper {
    fun Media.toModel(): MediaModel {
        val video = this as? Media.Video
        val sound = video?.sound
        return MediaModel(
            id = id,
            pinId = pinId,
            mimeType = mimeType,
            width = width,
            height = height,
            animated = animated,
            byteSize = byteSize,
            contentHash = contentHash,
            storageKey = storageKey,
            createdAt = createdAt,
            frames = frames,
            durationMillis = video?.run { duration.toMillis() },
            videoBitRate = video?.videoBitRate,
            audioChannels = sound?.channels,
            audioBitRate = sound?.bitRate,
        )
    }

    fun MediaModel.toDomain(): Media =
        when {
            mimeType.startsWith("video/") -> toVideo()
            animated ->
                Media.AnimatedImage(
                    id = id,
                    pinId = pinId,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    byteSize = byteSize,
                    contentHash = contentHash,
                    storageKey = storageKey,
                    createdAt = createdAt,
                    frames = frames,
                )
            else ->
                Media.StillImage(
                    id = id,
                    pinId = pinId,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    byteSize = byteSize,
                    contentHash = contentHash,
                    storageKey = storageKey,
                    createdAt = createdAt,
                )
        }

    // The columns are nullable for an image's sake: a video row missing one is a defect, not a state.
    private fun MediaModel.toVideo(): Media.Video {
        val durationMillis = checkNotNull(durationMillis) { "Video $id has no duration" }
        val sound =
            if (audioChannels == null && audioBitRate == null) {
                null
            } else {
                Media.Sound(
                    checkNotNull(audioChannels) { "Video $id has no audio channels" },
                    checkNotNull(audioBitRate) { "Video $id has no audio rate" },
                )
            }
        return Media.Video(
            id = id,
            pinId = pinId,
            mimeType = mimeType,
            width = width,
            height = height,
            byteSize = byteSize,
            contentHash = contentHash,
            storageKey = storageKey,
            createdAt = createdAt,
            frames = frames,
            duration = Duration.ofMillis(durationMillis),
            videoBitRate = checkNotNull(videoBitRate) { "Video $id has no video rate" },
            sound = sound,
        )
    }
}
