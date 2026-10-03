package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinMediaDownloadInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.MediaOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinMediaStateDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http.ByteRangeResponse
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http.RangeHeader
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.MediaMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMediaStateMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.DeletePinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.GetPinMediaRendition
import fr.geoffreyCoulaud.pinryReborn.api.usecases.GetPinMediaRendition.Companion.ENCODER_VERSION
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMediaStatus
import fr.geoffreyCoulaud.pinryReborn.api.usecases.RequestPinMediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ServedMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.MediaRenditionSizeInvalidError
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
class MediaController(
    private val setPinMedia: SetPinMedia,
    private val getPinMediaRendition: GetPinMediaRendition,
    private val deletePinMedia: DeletePinMedia,
    private val requestPinMediaDownload: RequestPinMediaDownload,
    private val resolvePinMediaState: ResolvePinMediaState,
    private val mediaStore: MediaStore,
    private val renditionCache: RenditionCache,
    private val renditionsConfig: RenditionsConfig,
    private val securityIdentity: SecurityIdentity,
) {
    @PUT
    @Path("/{pinId}/media")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = SET_MEDIA_OPERATION_SUMMARY)
    @APIResponse(
        responseCode = "201",
        description = "Image created",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = MediaOutputDto::class),
            ),
        ],
    )
    @APIResponse(
        responseCode = "200",
        description = "Image replaced",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = MediaOutputDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "400", description = INVALID_REQUEST,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["MEDIA_SOURCE_URL_INVALID", "VALIDATION_ERROR", "MALFORMED_BODY"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.MEDIA_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.MEDIA_NOT_FOUND)
    @APIResponse(responseCode = "413", description = TOO_LARGE,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["MEDIA_TOO_LARGE"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    @APIResponse(responseCode = "422",
        description = "The upload is not an image the server reads, or it is past media.max_pixels",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["MEDIA_INVALID"])]))])
    fun setMedia(pinId: UUID, @RestForm("file") @NotNull file: FileUpload): RestResponse<MediaOutputDto> {
        val requester = securityIdentity.getUser()
        val result = Files.newInputStream(file.uploadedFile()).use { setPinMedia.set(pinId, requester, it) }
        val dto = result.media.toDto()
        val status = if (result.replaced) RestResponse.Status.OK else RestResponse.Status.CREATED
        return ResponseBuilder.create(status, dto).build()
    }

    @GET
    @Path("/{pinId}/media")
    @APIResponse(responseCode = "200", description = "The original, or a WebP rendition",
        content = [Content(mediaType = "image/*")])
    @APIResponse(responseCode = "206", description = "The requested byte range of the original, Content-Range set",
        content = [Content(mediaType = "image/*")])
    @APIResponse(responseCode = "400", description = "The size names no rendition",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["MEDIA_RENDITION_SIZE_INVALID"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.MEDIA_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.MEDIA_NOT_FOUND)
    @APIResponse(responseCode = "416", ref = SharedRefusalsFilter.RANGE_NOT_SATISFIABLE)
    fun getMedia(
        pinId: UUID,
        @QueryParam("size") size: String?,
        @QueryParam("animated") animated: Boolean?,
        @HeaderParam("If-None-Match") ifNoneMatch: String?,
        @HeaderParam("Range") rangeHeader: String?,
    ): RestResponse<StreamingOutput> {
        val requester = securityIdentity.getUser()
        val requestedPx = size?.let { resolveSizePx(it) }
        val served = getPinMediaRendition.get(pinId, requester, requestedPx, animated ?: true)
        // A statement, not `return when`: the expression form compiles a synthetic
        // `NoWhenBranchMatchedException` branch that Kover counts as uncovered.
        val response: RestResponse<StreamingOutput>
        when (served) {
            is ServedMedia.Original -> response = serveOriginal(served.media, ifNoneMatch, rangeHeader)
            is ServedMedia.Rendition -> response = serveRendition(served, ifNoneMatch)
        }
        return response
    }

    private fun resolveSizePx(size: String): Int =
        (RenditionSize.fromName(size) ?: throw MediaRenditionSizeInvalidError()).pxFrom(renditionsConfig)

    private fun serveOriginal(media: Media, ifNoneMatch: String?, rangeHeader: String?): RestResponse<StreamingOutput> {
        val etag = "\"${media.contentHash}\""
        if (ifNoneMatch == etag) return RestResponse.notModified()
        val range = RangeHeader.parse(rangeHeader, media.byteSize)
        return ByteRangeResponse.builder(mediaStore.openStream(media.storageKey), media.byteSize, range)
            .header("Content-Type", media.mimeType)
            .header("ETag", etag)
            .header("Cache-Control", "private, must-revalidate")
            .build()
    }

    private fun serveRendition(rendition: ServedMedia.Rendition, ifNoneMatch: String?): RestResponse<StreamingOutput> {
        val etag = renditionEtag(rendition)
        if (ifNoneMatch == etag) return RestResponse.notModified()
        val streamingOutput = StreamingOutput { output ->
            // The use case just confirmed/stored this entry; a null here means a concurrent evict
            // removed it (rare race) -> treat as gone.
            (renditionCache.openStream(rendition.mediaId, rendition.key) ?: throw MediaDoesNotExistError())
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
    private fun renditionEtag(rendition: ServedMedia.Rendition): String =
        "\"$ENCODER_VERSION-${rendition.mediaId}-${rendition.effectivePx}-${if (rendition.animated) "a" else "s"}\""

    @DELETE
    @Path("/{pinId}/media")
    @APIResponse(responseCode = "204", description = "No Content")
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.MEDIA_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.MEDIA_NOT_FOUND)
    fun deleteMedia(pinId: UUID): RestResponse<Void> {
        val requester = securityIdentity.getUser()
        deletePinMedia.delete(pinId = pinId, requester = requester)
        return RestResponse.noContent()
    }

    @PUT
    @Path("/{pinId}/media")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = SET_MEDIA_OPERATION_SUMMARY)
    @APIResponse(
        responseCode = "202",
        description = "Download accepted",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = PinMediaStateDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "400", description = INVALID_REQUEST,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["MEDIA_SOURCE_URL_INVALID", "VALIDATION_ERROR", "MALFORMED_BODY"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.MEDIA_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.MEDIA_NOT_FOUND)
    @APIResponse(responseCode = "413", description = TOO_LARGE,
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["MEDIA_TOO_LARGE"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun requestMediaDownload(
        pinId: UUID,
        @Valid @NotNull body: PinMediaDownloadInputDto,
    ): RestResponse<PinMediaStateDto> {
        val requester = securityIdentity.getUser()
        requestPinMediaDownload.request(pinId, requester, body.sourceUrl)
        val dto = PinMediaState(PinMediaStatus.PENDING, null, null, null).toDto(pinId)
        return ResponseBuilder.create<PinMediaStateDto>(RestResponse.Status.ACCEPTED, dto)
            .header(HttpHeaders.LOCATION, "/api/v1/pins/$pinId/media/status")
            .build()
    }

    @GET
    @Path("/{pinId}/media/status")
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = MediaType.APPLICATION_JSON,
            schema = Schema(implementation = PinMediaStateDto::class))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.MEDIA_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.MEDIA_NOT_FOUND)
    fun getMediaStatus(pinId: UUID): RestResponse<PinMediaStateDto> {
        val requester = securityIdentity.getUser()
        val state = resolvePinMediaState.resolve(pinId = pinId, requester = requester)
        return RestResponse.ok(state.toDto(pinId))
    }

    private companion object {
        // Shared by `setMedia` and `requestMediaDownload`: both are `PUT /{pinId}/media`, and
        // SmallRye OpenAPI merges the two `@Consumes`-differentiated methods into a single
        // Operation. Keeping the summary in one place avoids the two annotations drifting apart.
        const val SET_MEDIA_OPERATION_SUMMARY =
            "Set the pin's canonical image (upload bytes, or request a server-side fetch)"

        // Both arms declare these identically, SmallRye merging them (spec 2026-09-23, section 3).
        const val TOO_LARGE = "MEDIA_TOO_LARGE: the upload is past media.max_image_bytes for an image, " +
            "media.max_video_bytes for a video. BODY_TOO_LARGE: the Content-Length is past " +
            "quarkus.http.limits.max-body-size, which is above both; a chunked body past it gets a 413 with no body"
        const val INVALID_REQUEST = "The upload has no file part, the body is not JSON or breaks a constraint, " +
            "or the source URL is not an http or https address"
    }
}
