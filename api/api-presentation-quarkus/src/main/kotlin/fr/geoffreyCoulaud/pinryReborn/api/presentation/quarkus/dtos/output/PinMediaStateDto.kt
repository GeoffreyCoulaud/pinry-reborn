package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

data class PinMediaStateDto(
    val status: PinMediaStatusDto,
    val url: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long? = null,
    /** A video's, null for an image. */
    val durationMillis: Long? = null,
    /** The rates in bits per second, each null for an image or a video without that track. */
    val videoBitRate: Long? = null,
    val audioChannels: Int? = null,
    val audioBitRate: Long? = null,
    val reasonCode: DownloadReasonDto? = null,
    val message: String? = null,
    val replacement: ReplacementDto? = null,
) {
    data class ReplacementDto(
        val status: DownloadStatusDto,
        val reasonCode: DownloadReasonDto? = null,
        val message: String? = null,
    )
}
