package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.MediaModel
import java.time.Duration

object MediaModelMapper {
    fun Media.toModel() = MediaModel(
        id = id, pinId = pinId, mimeType = mimeType, width = width, height = height, animated = animated,
        byteSize = byteSize, contentHash = contentHash, storageKey = storageKey, createdAt = createdAt,
        frames = frames, durationMillis = duration?.toMillis(),
    )

    fun MediaModel.toDomain() = Media(
        id = id, pinId = pinId, mimeType = mimeType, width = width, height = height, animated = animated,
        byteSize = byteSize, contentHash = contentHash, storageKey = storageKey, createdAt = createdAt,
        frames = frames, duration = durationMillis?.let(Duration::ofMillis),
    )
}
