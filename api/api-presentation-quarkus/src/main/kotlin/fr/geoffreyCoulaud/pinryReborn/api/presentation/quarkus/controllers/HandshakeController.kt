package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.ContractConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.HandshakeOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.ImportUploadBounds
import jakarta.annotation.security.PermitAll
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path

@Path("/api/v1/handshake")
class HandshakeController(
    private val mediaConfig: MediaConfig,
    private val renditionsConfig: RenditionsConfig,
    private val contractConfig: ContractConfig,
    private val importBounds: ImportUploadBounds,
) {
    @GET
    @PermitAll
    fun getHandshake(): HandshakeOutputDto = HandshakeOutputDto(
        contractVersion = contractConfig.infoVersion(),
        limits = HandshakeOutputDto.LimitsDto(
            maxFileBytes = mediaConfig.maxFileBytes(),
            maxPixels = mediaConfig.maxPixels(),
            mediaTypes = MediaFormat.entries.map { it.mimeType },
            maxImportChunkBytes = importBounds.maxChunkBytes,
            maxImportArchiveBytes = importBounds.maxArchiveBytes,
        ),
        renditionSizes = HandshakeOutputDto.RenditionSizesDto(
            tiny = renditionsConfig.tiny(),
            small = renditionsConfig.small(),
            medium = renditionsConfig.medium(),
            large = renditionsConfig.large(),
        ),
    )
}
