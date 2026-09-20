package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

/**
 * What a client reads before its first call: the contract it negotiates on, and the numbers this
 * deployment configures (`docs/adr/0024-three-projects-share-one-repository.md`, decision 6).
 */
data class HandshakeOutputDto(
    val contractVersion: String,
    val limits: LimitsDto,
    val renditionSizes: RenditionSizesDto,
) {
    /** What an upload is refused for, so a client refuses it before sending the bytes. */
    data class LimitsDto(
        val maxFileBytes: Long,
        val maxPixels: Long,
        /** The media types the storage accepts; anything else the probe refuses. */
        val mediaTypes: List<String>,
    )

    /** Shortest side, in pixels, each `size` of `GET /api/v1/pins/{pinId}/image` answers. */
    data class RenditionSizesDto(
        val tiny: Int,
        val small: Int,
        val medium: Int,
        val large: Int,
    )
}
