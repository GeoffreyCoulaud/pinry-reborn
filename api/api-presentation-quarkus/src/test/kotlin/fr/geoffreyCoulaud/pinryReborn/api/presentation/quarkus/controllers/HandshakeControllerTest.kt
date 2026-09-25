package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.ImageFormat
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.ContractConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.ImagesConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.ImportUploadBounds
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HandshakeControllerTest {
    private val imagesConfig = mockk<ImagesConfig>()
    private val renditionsConfig = mockk<RenditionsConfig>()
    private val contractConfig = mockk<ContractConfig>()
    private val importBounds = ImportUploadBounds(MAX_IMPORT_CHUNK_BYTES, MAX_IMPORT_ARCHIVE_BYTES)
    private val controller = HandshakeController(imagesConfig, renditionsConfig, contractConfig, importBounds)

    @Test
    fun `Given the deployment's configuration, Then the handshake carries it beside the contract version`() {
        // Given
        every { imagesConfig.maxFileBytes() } returns MAX_FILE_BYTES
        every { imagesConfig.maxPixels() } returns MAX_PIXELS
        every { renditionsConfig.tiny() } returns TINY
        every { renditionsConfig.small() } returns SMALL
        every { renditionsConfig.medium() } returns MEDIUM
        every { renditionsConfig.large() } returns LARGE
        every { contractConfig.infoVersion() } returns CONTRACT_VERSION

        // When
        val dto = controller.getHandshake()

        // Then
        assertEquals(CONTRACT_VERSION, dto.contractVersion)
        assertEquals(MAX_FILE_BYTES, dto.limits.maxFileBytes)
        assertEquals(MAX_PIXELS, dto.limits.maxPixels)
        assertEquals(MAX_IMPORT_CHUNK_BYTES, dto.limits.maxImportChunkBytes)
        assertEquals(MAX_IMPORT_ARCHIVE_BYTES, dto.limits.maxImportArchiveBytes)
        assertEquals(TINY, dto.renditionSizes.tiny)
        assertEquals(SMALL, dto.renditionSizes.small)
        assertEquals(MEDIUM, dto.renditionSizes.medium)
        assertEquals(LARGE, dto.renditionSizes.large)
    }

    @Test
    fun `Given the formats the probe accepts, Then the handshake publishes their media types`() {
        // Given
        every { imagesConfig.maxFileBytes() } returns MAX_FILE_BYTES
        every { imagesConfig.maxPixels() } returns MAX_PIXELS
        every { renditionsConfig.tiny() } returns TINY
        every { renditionsConfig.small() } returns SMALL
        every { renditionsConfig.medium() } returns MEDIUM
        every { renditionsConfig.large() } returns LARGE
        every { contractConfig.infoVersion() } returns CONTRACT_VERSION

        // When
        val mediaTypes = controller.getHandshake().limits.mediaTypes

        // Then: the stored formats themselves, so a format added to the enum reaches the client
        assertEquals(ImageFormat.entries.map { it.mimeType }, mediaTypes)
        assertEquals(listOf("image/png", "image/jpeg", "image/webp", "image/gif"), mediaTypes)
    }

    private companion object {
        const val CONTRACT_VERSION = "9.8.7"
        const val MAX_FILE_BYTES = 1_234_567L
        const val MAX_PIXELS = 7_654_321L
        const val MAX_IMPORT_CHUNK_BYTES = 2_345_678L
        const val MAX_IMPORT_ARCHIVE_BYTES = 98_765_432_109L
        const val TINY = 11
        const val SMALL = 22
        const val MEDIUM = 33
        const val LARGE = 44
    }
}
