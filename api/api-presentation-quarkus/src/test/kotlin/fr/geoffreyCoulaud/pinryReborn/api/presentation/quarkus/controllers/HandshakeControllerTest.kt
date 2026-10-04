package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.ContractConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.ImportUploadBounds
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HandshakeControllerTest {
    private val mediaConfig = mockk<MediaConfig>()
    private val renditionsConfig = mockk<RenditionsConfig>()
    private val contractConfig = mockk<ContractConfig>()
    private val importBounds = ImportUploadBounds(MAX_IMPORT_CHUNK_BYTES, MAX_IMPORT_ARCHIVE_BYTES)
    private val controller = HandshakeController(mediaConfig, renditionsConfig, contractConfig, importBounds)

    @Test
    fun `Given the deployment's configuration, Then the handshake carries it beside the contract version`() {
        // Given
        every { mediaConfig.maxImageBytes() } returns MAX_IMAGE_BYTES
        every { mediaConfig.maxVideoBytes() } returns MAX_VIDEO_BYTES
        every { mediaConfig.maxVideoSeconds() } returns MAX_VIDEO_SECONDS
        every { mediaConfig.maxPixelsPerFrame() } returns MAX_PIXELS_PER_FRAME
        every { renditionsConfig.tiny() } returns TINY
        every { renditionsConfig.small() } returns SMALL
        every { renditionsConfig.medium() } returns MEDIUM
        every { renditionsConfig.large() } returns LARGE
        every { contractConfig.infoVersion() } returns CONTRACT_VERSION

        // When
        val dto = controller.getHandshake()

        // Then
        assertEquals(CONTRACT_VERSION, dto.contractVersion)
        assertEquals(MAX_IMAGE_BYTES, dto.limits.maxImageBytes)
        assertEquals(MAX_VIDEO_BYTES, dto.limits.maxVideoBytes)
        assertEquals(MAX_VIDEO_SECONDS, dto.limits.maxVideoSeconds)
        assertEquals(MAX_PIXELS_PER_FRAME, dto.limits.maxPixelsPerFrame)
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
        every { mediaConfig.maxImageBytes() } returns MAX_IMAGE_BYTES
        every { mediaConfig.maxVideoBytes() } returns MAX_VIDEO_BYTES
        every { mediaConfig.maxVideoSeconds() } returns MAX_VIDEO_SECONDS
        every { mediaConfig.maxPixelsPerFrame() } returns MAX_PIXELS_PER_FRAME
        every { renditionsConfig.tiny() } returns TINY
        every { renditionsConfig.small() } returns SMALL
        every { renditionsConfig.medium() } returns MEDIUM
        every { renditionsConfig.large() } returns LARGE
        every { contractConfig.infoVersion() } returns CONTRACT_VERSION

        // When
        val mediaTypes = controller.getHandshake().limits.mediaTypes

        // Then: the stored image formats themselves, so a format added to the enum reaches the client
        assertEquals(MediaFormat.entries.map { it.mimeType } + HandshakeController.VIDEO_UPLOAD_TYPES, mediaTypes)
        assertEquals(
            listOf(
                "image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm", "video/quicktime",
                "video/x-matroska", "video/x-m4v", "video/3gpp",
            ),
            mediaTypes,
        )
    }

    private companion object {
        const val CONTRACT_VERSION = "9.8.7"
        const val MAX_IMAGE_BYTES = 1_234_567L
        const val MAX_VIDEO_BYTES = 3_456_789L
        const val MAX_VIDEO_SECONDS = 99L
        const val MAX_PIXELS_PER_FRAME = 7_654_321L
        const val MAX_IMPORT_CHUNK_BYTES = 2_345_678L
        const val MAX_IMPORT_ARCHIVE_BYTES = 98_765_432_109L
        const val TINY = 11
        const val SMALL = 22
        const val MEDIUM = 33
        const val LARGE = 44
    }
}
