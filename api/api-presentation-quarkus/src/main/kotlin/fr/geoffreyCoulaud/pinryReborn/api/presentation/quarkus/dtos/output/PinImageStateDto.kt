package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

data class PinImageStateDto(
    val status: PinImageStatusDto,
    val url: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long? = null,
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
