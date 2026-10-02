package fr.geoffreyCoulaud.pinryReborn.api.domain.enums

enum class MediaFormat(
    val mimeType: String,
    val extension: String,
) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif"),
}
