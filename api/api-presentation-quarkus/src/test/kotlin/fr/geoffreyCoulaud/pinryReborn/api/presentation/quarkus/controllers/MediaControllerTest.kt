package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.MediaOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http.RangeNotSatisfiableException
import fr.geoffreyCoulaud.pinryReborn.api.usecases.DeletePinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.GetPinMediaRendition
import fr.geoffreyCoulaud.pinryReborn.api.usecases.RequestPinMediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ServedMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinMediaResult
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaRenditionSizeInvalidError
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.security.identity.SecurityIdentity
import jakarta.ws.rs.core.StreamingOutput
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class MediaControllerTest {
    private val setPinMedia = mockk<SetPinMedia>()
    private val getPinMediaRendition = mockk<GetPinMediaRendition>()
    private val deletePinMedia = mockk<DeletePinMedia>()
    private val requestPinMediaDownload = mockk<RequestPinMediaDownload>()
    private val resolvePinMediaState = mockk<ResolvePinMediaState>()
    private val mediaStore = mockk<MediaStore>()
    private val renditionCache = mockk<RenditionCache>()
    private val renditionsConfig = mockk<RenditionsConfig>()
    private val securityIdentity = mockk<SecurityIdentity>()
    private val controller = MediaController(
        setPinMedia = setPinMedia,
        getPinMediaRendition = getPinMediaRendition,
        deletePinMedia = deletePinMedia,
        requestPinMediaDownload = requestPinMediaDownload,
        resolvePinMediaState = resolvePinMediaState,
        mediaStore = mediaStore,
        renditionCache = renditionCache,
        renditionsConfig = renditionsConfig,
        securityIdentity = securityIdentity,
    )

    @TempDir
    lateinit var tempDir: Path

    private fun aMedia(pinId: UUID) = Media.StillImage(
        id = randomUUID(),
        pinId = pinId,
        mimeType = "image/png",
        width = 8,
        height = 6,
        byteSize = 4,
        contentHash = createRandomString(),
        storageKey = "originals/x/$pinId/y.png",
        createdAt = Instant.EPOCH,
    )

    private fun aUser() = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)

    @Test
    fun `Given a pin with no existing image, Then setMedia returns 201 with the created image`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val uploadedPath = Files.createTempFile(tempDir, "upload-", ".tmp")
        Files.write(uploadedPath, byteArrayOf(1, 2, 3))
        val fileUpload = mockk<FileUpload>()
        every { fileUpload.uploadedFile() } returns uploadedPath
        val media = aMedia(pinId)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { setPinMedia.set(pinId, user, any()) } returns SetPinMediaResult(media = media, replaced = false)

        // When
        val response = controller.setMedia(pinId, fileUpload)

        // Then
        assertEquals(201, response.status)
        val dto = response.entity as MediaOutputDto
        assertEquals(media.id, dto.id)
        assertEquals("/api/v1/pins/$pinId/media", dto.url)
    }

    @Test
    fun `Given a pin with an existing image, Then setMedia returns 200 with the replaced image`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val uploadedPath = Files.createTempFile(tempDir, "upload-", ".tmp")
        Files.write(uploadedPath, byteArrayOf(1, 2, 3))
        val fileUpload = mockk<FileUpload>()
        every { fileUpload.uploadedFile() } returns uploadedPath
        val media = aMedia(pinId)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { setPinMedia.set(pinId, user, any()) } returns SetPinMediaResult(media = media, replaced = true)

        // When
        val response = controller.setMedia(pinId, fileUpload)

        // Then
        assertEquals(200, response.status)
        val dto = response.entity as MediaOutputDto
        assertEquals(media.id, dto.id)
    }

    @Test
    fun `Given no size requested and a matching If-None-Match header, Then getMedia returns 304 without streaming`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val media = aMedia(pinId)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { getPinMediaRendition.get(pinId, user, null, null) } returns ServedMedia.Original(media)

        // When
        val response = controller.getMedia(
            pinId,
            size = null,
            animated = null,
            ifNoneMatch = "\"${media.contentHash}\"",
            rangeHeader = null,
        )

        // Then
        assertEquals(304, response.status)
        assertNull(response.entity)
        verify(exactly = 0) { mediaStore.openStream(any()) }
    }

    @Test
    fun `Given no size and no matching ETag, Then getMedia returns 200 with headers and streams the bytes`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val media = aMedia(pinId)
        val bytes = byteArrayOf(9, 8, 7, 6)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { getPinMediaRendition.get(pinId, user, null, null) } returns ServedMedia.Original(media)
        every { mediaStore.openStream(media.storageKey) } returns ByteArrayInputStream(bytes)

        // When
        val response = controller.getMedia(pinId, size = null, animated = null, ifNoneMatch = null, rangeHeader = null)

        // Then
        assertEquals(200, response.status)
        assertEquals(media.mimeType, response.getHeaderString("Content-Type"))
        assertEquals("\"${media.contentHash}\"", response.getHeaderString("ETag"))
        assertEquals("private, must-revalidate", response.getHeaderString("Cache-Control"))
        assertEquals(media.byteSize.toString(), response.getHeaderString("Content-Length"))
        assertEquals("bytes", response.getHeaderString("Accept-Ranges"))
        assertNull(response.getHeaderString("Content-Range"))

        val streamingOutput = response.entity as StreamingOutput
        val out = ByteArrayOutputStream()
        streamingOutput.write(out)
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test
    fun `Given an original response whose body is never written, Then the store's stream is never opened`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val media = aMedia(pinId)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { getPinMediaRendition.get(pinId, user, null, null) } returns ServedMedia.Original(media)

        // When
        controller.getMedia(pinId, size = null, animated = null, ifNoneMatch = null, rangeHeader = "bytes=1-2")

        // Then
        verify(exactly = 0) { mediaStore.openStream(any()) }
    }

    @Test
    fun `Given no size and a Range header, Then getMedia returns 206 with that slice of the original`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val media = aMedia(pinId)
        val bytes = byteArrayOf(9, 8, 7, 6)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { getPinMediaRendition.get(pinId, user, null, null) } returns ServedMedia.Original(media)
        every { mediaStore.openStream(media.storageKey) } returns ByteArrayInputStream(bytes)

        // When
        val response =
            controller.getMedia(pinId, size = null, animated = null, ifNoneMatch = null, rangeHeader = "bytes=1-2")

        // Then
        assertEquals(206, response.status)
        assertEquals("bytes 1-2/4", response.getHeaderString("Content-Range"))
        assertEquals("2", response.getHeaderString("Content-Length"))
        assertEquals("\"${media.contentHash}\"", response.getHeaderString("ETag"))
        val out = ByteArrayOutputStream()
        (response.entity as StreamingOutput).write(out)
        assertArrayEquals(byteArrayOf(8, 7), out.toByteArray())
    }

    @Test
    fun `Given a Range starting at the original's size, Then getMedia throws before opening the store`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        val media = aMedia(pinId)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { getPinMediaRendition.get(pinId, user, null, null) } returns ServedMedia.Original(media)

        // Then
        assertThrows(RangeNotSatisfiableException::class.java) {
            controller.getMedia(pinId, size = null, animated = null, ifNoneMatch = null, rangeHeader = "bytes=4-")
        }
        verify(exactly = 0) { mediaStore.openStream(any()) }
    }

    @Test
    fun `Given size small and a rendition, Then it serves image webp with a synthetic ETag`() {
        // Given
        val pinId = randomUUID()
        val mediaId = randomUUID()
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { renditionsConfig.small() } returns 240
        every { getPinMediaRendition.get(pinId, user, 240, null) } returns
            ServedMedia.Rendition(mediaId, "v2-240-a.webp")
        every { renditionCache.openStream(mediaId, "v2-240-a.webp") } returns ByteArrayInputStream(byteArrayOf(7, 7))

        // When
        val response =
            controller.getMedia(pinId, size = "small", animated = null, ifNoneMatch = null, rangeHeader = null)

        // Then
        assertEquals(200, response.status)
        assertEquals("image/webp", response.getHeaderString("Content-Type"))
        assertEquals("\"$mediaId-v2-240-a.webp\"", response.getHeaderString("ETag"))
        assertEquals("private, must-revalidate", response.getHeaderString("Cache-Control"))
        val streamingOutput = response.entity as StreamingOutput
        val out = ByteArrayOutputStream()
        streamingOutput.write(out)
        assertArrayEquals(byteArrayOf(7, 7), out.toByteArray())
    }

    @Test
    fun `Given animated is explicitly false, Then it is passed through`() {
        // Given
        val pinId = randomUUID()
        val mediaId = randomUUID()
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { renditionsConfig.small() } returns 240
        every { getPinMediaRendition.get(pinId, user, 240, false) } returns
            ServedMedia.Rendition(mediaId, "v2-240-s.webp")
        every { renditionCache.openStream(mediaId, "v2-240-s.webp") } returns ByteArrayInputStream(byteArrayOf(4))

        // When
        val response =
            controller.getMedia(pinId, size = "small", animated = false, ifNoneMatch = null, rangeHeader = null)

        // Then
        assertEquals(200, response.status)
        verify { getPinMediaRendition.get(pinId, user, 240, false) }
    }

    @Test
    fun `Given a degraded and a whole rendition of one animated request, Then their ETags differ`() {
        // Given: the same request, served the static key under a tighter bound, then the animated one
        val pinId = randomUUID()
        val mediaId = randomUUID()
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { renditionsConfig.small() } returns 240
        every { getPinMediaRendition.get(pinId, user, 240, true) } returnsMany listOf(
            ServedMedia.Rendition(mediaId, "v2-240-s.webp"),
            ServedMedia.Rendition(mediaId, "v2-240-a.webp"),
        )

        // When
        val degraded = controller.getMedia(pinId, "small", animated = true, ifNoneMatch = null, rangeHeader = null)
        val whole = controller.getMedia(pinId, "small", animated = true, ifNoneMatch = null, rangeHeader = null)

        // Then
        assertEquals("\"$mediaId-v2-240-s.webp\"", degraded.getHeaderString("ETag"))
        assertEquals("\"$mediaId-v2-240-a.webp\"", whole.getHeaderString("ETag"))
    }

    @Test
    fun `Given size small and a matching synthetic ETag, Then getMedia returns 304 without streaming`() {
        // Given
        val pinId = randomUUID()
        val mediaId = randomUUID()
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { renditionsConfig.small() } returns 240
        every { getPinMediaRendition.get(pinId, user, 240, null) } returns
            ServedMedia.Rendition(mediaId, "v2-240-a.webp")

        // When
        val response = controller.getMedia(
            pinId,
            size = "small",
            animated = null,
            ifNoneMatch = "\"$mediaId-v2-240-a.webp\"",
            rangeHeader = null,
        )

        // Then
        assertEquals(304, response.status)
        assertNull(response.entity)
        verify(exactly = 0) { renditionCache.openStream(any(), any()) }
    }

    @Test
    fun `Given an unknown size, Then it throws MediaRenditionSizeInvalidError`() {
        // Given
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user

        // Then
        assertThrows(MediaRenditionSizeInvalidError::class.java) {
            controller.getMedia(randomUUID(), size = "huge", animated = null, ifNoneMatch = null, rangeHeader = null)
        }
    }

    @Test
    fun `Given a rendition cache entry evicted concurrently, Then writing the body throws MediaDoesNotExistError`() {
        // Given
        val pinId = randomUUID()
        val mediaId = randomUUID()
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { renditionsConfig.small() } returns 240
        every { getPinMediaRendition.get(pinId, user, 240, null) } returns
            ServedMedia.Rendition(mediaId, "v2-240-a.webp")
        every { renditionCache.openStream(mediaId, "v2-240-a.webp") } returns null

        // When
        val response =
            controller.getMedia(pinId, size = "small", animated = null, ifNoneMatch = null, rangeHeader = null)
        val streamingOutput = response.entity as StreamingOutput

        // Then
        assertEquals(200, response.status)
        assertThrows(MediaDoesNotExistError::class.java) {
            streamingOutput.write(ByteArrayOutputStream())
        }
    }

    @Test
    fun `Given a valid request, Then deleteMedia returns 204`() {
        // Given
        val pinId = randomUUID()
        val user = aUser()
        every { securityIdentity.getAttribute<User>("user") } returns user
        every { deletePinMedia.delete(pinId = pinId, requester = user) } returns Unit

        // When
        val response = controller.deleteMedia(pinId)

        // Then
        assertEquals(204, response.status)
    }
}
