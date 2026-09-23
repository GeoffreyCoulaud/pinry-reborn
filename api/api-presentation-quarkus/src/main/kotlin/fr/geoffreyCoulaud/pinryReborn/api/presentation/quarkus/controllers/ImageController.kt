package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Image
import fr.geoffreyCoulaud.pinryReborn.api.domain.images.ImageStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.images.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.ImagesConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinImageDownloadInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ImageOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinImageStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ImageMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinImageStateMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.DeletePinImage
import fr.geoffreyCoulaud.pinryReborn.api.usecases.GetPinImageRendition
import fr.geoffreyCoulaud.pinryReborn.api.usecases.GetPinImageRendition.Companion.ENCODER_VERSION
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageStatus
import fr.geoffreyCoulaud.pinryReborn.api.usecases.RequestPinImageDownload
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinImageState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ServedImage
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinImage
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImageDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImageRenditionSizeInvalidError
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.StreamingOutput
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.nio.file.Files
import java.util.UUID

@Path("/api/v1/pins")
@Authenticated
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class ImageController(
    private val setPinImage: SetPinImage,
    private val getPinImageRendition: GetPinImageRendition,
    private val deletePinImage: DeletePinImage,
    private val requestPinImageDownload: RequestPinImageDownload,
    private val resolvePinImageState: ResolvePinImageState,
    private val imageStore: ImageStore,
    private val imagesConfig: ImagesConfig,
    private val renditionCache: RenditionCache,
    private val renditionsConfig: RenditionsConfig,
    private val securityIdentity: SecurityIdentity,
) {
    @PUT
    @Path("/{pinId}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = SET_IMAGE_OPERATION_SUMMARY)
    @APIResponse(
        responseCode = "201",
        description = "Image created",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = ImageOutputDto::class),
            ),
        ],
    )
    @APIResponse(
        responseCode = "200",
        description = "Image replaced",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = ImageOutputDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "400", description = INVALID_REQUEST,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["IMAGE_SOURCE_URL_INVALID", "VALIDATION_ERROR", "MALFORMED_BODY"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.IMAGE_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.IMAGE_NOT_FOUND)
    @APIResponse(responseCode = "413", description = TOO_LARGE,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["IMAGE_TOO_LARGE", "BODY_TOO_LARGE"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    @APIResponse(responseCode = "422",
        description = "The upload is not an image the server reads, or it is past images.max_pixels",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["IMAGE_INVALID"])]))])
    fun setImage(pinId: UUID, @RestForm("file") @NotNull file: FileUpload): RestResponse<ImageOutputDto> {
        val requester = securityIdentity.getUser()
        val result = Files.newInputStream(file.uploadedFile()).use { upload ->
            setPinImage.set(
                pinId = pinId,
                requester = requester,
                upload = upload,
                maxBytes = imagesConfig.maxFileBytes(),
                maxPixels = imagesConfig.maxPixels(),
            )
        }
        val dto = result.image.toDto()
        val status = if (result.replaced) RestResponse.Status.OK else RestResponse.Status.CREATED
        return ResponseBuilder.create(status, dto).build()
    }

    @GET
    @Path("/{pinId}/image")
    @APIResponse(responseCode = "200", description = "The original, or a WebP rendition",
        content = [Content(mediaType = "image/*")])
    @APIResponse(responseCode = "400", description = "The size names no rendition",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["IMAGE_RENDITION_SIZE_INVALID"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.IMAGE_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.IMAGE_NOT_FOUND)
    fun getImage(
        pinId: UUID,
        @QueryParam("size") size: String?,
        @QueryParam("animated") animated: Boolean?,
        @HeaderParam("If-None-Match") ifNoneMatch: String?,
    ): RestResponse<StreamingOutput> {
        val requester = securityIdentity.getUser()
        val requestedPx = size?.let { resolveSizePx(it) }
        val served = getPinImageRendition.get(pinId, requester, requestedPx, animated ?: true)
        // A statement, not `return when`: the expression form compiles a synthetic
        // `NoWhenBranchMatchedException` branch that Kover counts as uncovered.
        val response: RestResponse<StreamingOutput>
        when (served) {
            is ServedImage.Original -> response = serveOriginal(served.image, ifNoneMatch)
            is ServedImage.Rendition -> response = serveRendition(served, ifNoneMatch)
        }
        return response
    }

    private fun resolveSizePx(size: String): Int =
        (RenditionSize.fromName(size) ?: throw ImageRenditionSizeInvalidError()).pxFrom(renditionsConfig)

    private fun serveOriginal(image: Image, ifNoneMatch: String?): RestResponse<StreamingOutput> {
        // Kotlin's `==` is null-safe (delegates to `equals`), so a null `ifNoneMatch` simply
        // compares unequal to `image.contentHash` without a separate null check/branch.
        if (ifNoneMatch == image.contentHash) return RestResponse.notModified()
        val streamingOutput = StreamingOutput { output ->
            imageStore.openStream(image.storageKey).use { it.copyTo(output) }
        }
        return ResponseBuilder.ok(streamingOutput)
            .header("Content-Type", image.mimeType)
            .header("ETag", image.contentHash)
            .header("Cache-Control", "private, must-revalidate")
            .header("Content-Length", image.byteSize)
            .build()
    }

    private fun serveRendition(rendition: ServedImage.Rendition, ifNoneMatch: String?): RestResponse<StreamingOutput> {
        val etag = renditionEtag(rendition)
        if (ifNoneMatch == etag) return RestResponse.notModified()
        val streamingOutput = StreamingOutput { output ->
            // The use case just confirmed/stored this entry; a null here means a concurrent evict
            // removed it (rare race) -> treat as gone.
            (renditionCache.openStream(rendition.imageId, rendition.key) ?: throw ImageDoesNotExistError())
                .use { it.copyTo(output) }
        }
        return ResponseBuilder.ok(streamingOutput)
            .header("Content-Type", "image/webp")
            .header("ETag", etag)
            .header("Cache-Control", "private, must-revalidate")
            .build()
    }

    // The encoder version is imported from the use case that builds the cache key rather than
    // duplicated here, so a bump invalidates the cached bytes and their validator together.
    private fun renditionEtag(rendition: ServedImage.Rendition): String =
        "$ENCODER_VERSION-${rendition.imageId}-${rendition.effectivePx}-${if (rendition.animated) "a" else "s"}"

    @DELETE
    @Path("/{pinId}/image")
    @APIResponse(responseCode = "204", description = "No Content")
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.IMAGE_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.IMAGE_NOT_FOUND)
    fun deleteImage(pinId: UUID): RestResponse<Void> {
        val requester = securityIdentity.getUser()
        deletePinImage.delete(pinId = pinId, requester = requester)
        return RestResponse.noContent()
    }

    @PUT
    @Path("/{pinId}/image")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = SET_IMAGE_OPERATION_SUMMARY)
    @APIResponse(
        responseCode = "202",
        description = "Download accepted",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = PinImageStateDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "400", description = INVALID_REQUEST,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["IMAGE_SOURCE_URL_INVALID", "VALIDATION_ERROR", "MALFORMED_BODY"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.IMAGE_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.IMAGE_NOT_FOUND)
    @APIResponse(responseCode = "413", description = TOO_LARGE,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["IMAGE_TOO_LARGE", "BODY_TOO_LARGE"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun requestImageDownload(
        pinId: UUID,
        @Valid @NotNull body: PinImageDownloadInputDto,
    ): RestResponse<PinImageStateDto> {
        val requester = securityIdentity.getUser()
        requestPinImageDownload.request(pinId, requester, body.sourceUrl)
        val dto = PinImageState(PinImageStatus.PENDING, null, null, null).toDto(pinId)
        return ResponseBuilder.create<PinImageStateDto>(RestResponse.Status.ACCEPTED, dto)
            .header(HttpHeaders.LOCATION, "/api/v1/pins/$pinId/image/status")
            .build()
    }

    @GET
    @Path("/{pinId}/image/status")
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = MediaType.APPLICATION_JSON,
            schema = Schema(implementation = PinImageStateDto::class))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.IMAGE_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.IMAGE_NOT_FOUND)
    fun getImageStatus(pinId: UUID): RestResponse<PinImageStateDto> {
        val requester = securityIdentity.getUser()
        val state = resolvePinImageState.resolve(pinId = pinId, requester = requester)
        return RestResponse.ok(state.toDto(pinId))
    }

    private companion object {
        // Shared by `setImage` and `requestImageDownload`: both are `PUT /{pinId}/image`, and
        // SmallRye OpenAPI merges the two `@Consumes`-differentiated methods into a single
        // Operation. Keeping the summary in one place avoids the two annotations drifting apart.
        const val SET_IMAGE_OPERATION_SUMMARY =
            "Set the pin's canonical image (upload bytes, or request a server-side fetch)"

        // Both arms declare these identically, SmallRye merging them (spec 2026-09-23, section 3).
        const val TOO_LARGE = "IMAGE_TOO_LARGE: the upload is past images.max_file_bytes. BODY_TOO_LARGE: the " +
            "Content-Length is past quarkus.http.limits.max-body-size, which is above images.max_file_bytes; " +
            "a chunked body past it gets a 413 with no body"
        const val INVALID_REQUEST = "The upload has no file part, the body is not JSON or breaks a constraint, " +
            "or the source URL is not an http or https address"
    }
}
