package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ImageDownloadListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ImageDownloadDtoMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.serialization.Base64Json
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ImageDownloads
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse
import java.util.UUID

/**
 * `/api/v1/me/image-downloads`: the caller's downloads that are running or failed, a success
 * leaving the pin and no row (spec `docs/specs/2026-09-10-web-application.md`, section 4.4).
 */
@Path("/api/v1/me/image-downloads")
@Authenticated
class MeImageDownloadController(
    private val imageDownloads: ImageDownloads,
    private val securityIdentity: SecurityIdentity,
) {
    @GET
    @Operation(
        summary = "List the caller's running and failed image downloads",
        description = "One page, newest request first. A recycled pin's download is left out, like every other read.",
    )
    @APIResponse(
        responseCode = "200",
        description = "One page of the caller's downloads, running and failed",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = ImageDownloadListOutputDto::class),
            ),
        ],
    )
    fun listImageDownloads(
        @QueryParam("cursor") @Base64Json cursorInput: CursorDto? = null,
        @QueryParam("pageSize") pageSizeInput: Int? = null,
    ): ImageDownloadListOutputDto =
        imageDownloads
            .list(securityIdentity.getUser(), cursorInput?.toDomain(), pageSizeInput ?: DEFAULT_PAGE_SIZE)
            .toDto()

    @DELETE
    @Path("/{pinId}")
    @Operation(summary = "Drop one settled download", description = "The pin and its image are untouched.")
    @APIResponse(responseCode = "204", description = "Download dropped")
    @APIResponse(
        responseCode = "404",
        description = "IMAGE_DOES_NOT_EXIST: no download of the caller carries this pin id",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    @APIResponse(
        responseCode = "409",
        description = "IMAGE_DOWNLOAD_IN_PROGRESS: the download is still running, and the worker owns its row",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun deleteImageDownload(pinId: UUID): RestResponse<Void> {
        imageDownloads.delete(securityIdentity.getUser(), pinId)
        return RestResponse.noContent()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
