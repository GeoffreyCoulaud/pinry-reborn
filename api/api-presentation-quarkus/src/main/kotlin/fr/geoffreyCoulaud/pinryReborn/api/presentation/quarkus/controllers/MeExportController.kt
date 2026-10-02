package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http.ByteRangeResponse
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http.ContentDispositionFileName
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http.RangeHeader
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.UserDataExportDtoMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.ReauthenticationHeader
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.serialization.Base64Json
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exports.OpenedExport
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exports.UserDataExportDeleter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exports.UserDataExportDownloader
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exports.UserDataExportGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exports.UserDataExportRequester
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.StreamingOutput
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder
import java.time.Instant
import java.util.UUID

/**
 * `/api/v1/me/exports`: request, track, download and destroy the caller's own user data export
 * archives (spec `docs/specs/2026-07-22-user-data-export.md` §7). All endpoints are owner-scoped
 * through the use cases they delegate to (`403` for a non-owner, `404` for an unknown id).
 */
@Path("/api/v1/me/exports")
@Authenticated
class MeExportController(
    private val requester: UserDataExportRequester,
    private val getter: UserDataExportGetter,
    private val downloader: UserDataExportDownloader,
    private val deleter: UserDataExportDeleter,
    private val securityIdentity: SecurityIdentity,
) {
    @POST
    @Operation(
        summary = "Request an export of the caller's data",
        description = "Queued, not built: the archive is downloadable once the export reads READY. " +
            "Needs the reauthentication header.",
    )
    @APIResponse(
        responseCode = "202",
        description = "Export requested and queued",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = UserDataExportOutputDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.UNSUPPORTED_REAUTHENTICATION_FACTOR)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.REAUTHENTICATION_HEADER_FAILED)
    @APIResponse(responseCode = "409", description = "This account already has a pending export",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["EXPORT_ALREADY_IN_PROGRESS"])]))])
    @APIResponse(responseCode = "429", description = "The attempt limiter, or the last request is inside " +
        "exports.minimum_interval; Retry-After names the wait",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["EXPORT_TOO_SOON", "TOO_MANY_AUTHENTICATION_ATTEMPTS"])]))])
    fun requestExport(
        @HeaderParam(ReauthenticationHeader.HEADER) reauthHeader: String?,
    ): RestResponse<UserDataExportOutputDto> {
        val user = securityIdentity.getUser()
        val factor = ReauthenticationHeader.parsePasswordFactor(reauthHeader)
        val export = requester.request(user, factor)
        return ResponseBuilder.create(RestResponse.Status.ACCEPTED, export.toDto()).build()
    }

    @GET
    @APIResponse(
        responseCode = "200",
        description = "One page of the caller's exports, every state included",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = UserDataExportListOutputDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.UNREADABLE_QUERY)
    fun listExports(
        @QueryParam("cursor") @Base64Json cursorInput: CursorDto? = null,
        @QueryParam("pageSize") pageSizeInput: Int? = null,
    ): RestResponse<UserDataExportListOutputDto> {
        val user = securityIdentity.getUser()
        val pageSize = pageSizeInput ?: DEFAULT_PAGE_SIZE
        val cursor = cursorInput?.toDomain()
        return RestResponse.ok(getter.list(user, cursor, pageSize).toDto())
    }

    @GET
    @Path("/{id}")
    @APIResponse(
        responseCode = "200",
        description = "The export's state and, once READY, its size, digest and expiry",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = UserDataExportOutputDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.EXPORT_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.EXPORT_NOT_FOUND)
    fun getExport(id: UUID): RestResponse<UserDataExportOutputDto> {
        val user = securityIdentity.getUser()
        return RestResponse.ok(getter.get(user, id).toDto())
    }

    @GET
    @Path("/{id}/download")
    @Operation(
        summary = "Download the archive",
        description = "Whole, or one byte range through the Range header. ETag carries the archive's SHA-256.",
    )
    @APIResponse(
        responseCode = "200",
        description = "The whole archive",
        content = [Content(mediaType = ARCHIVE_MEDIA_TYPE)],
    )
    @APIResponse(
        responseCode = "206",
        description = "The requested byte range, Content-Range set",
        content = [Content(mediaType = ARCHIVE_MEDIA_TYPE)],
    )
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.EXPORT_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.EXPORT_NOT_FOUND)
    @APIResponse(responseCode = "409", description = "The export is still pending or has failed",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["EXPORT_NOT_READY"])]))])
    @APIResponse(responseCode = "410",
        description = "The archive expired, was deleted or was superseded by a newer export",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["EXPORT_GONE"])]))])
    @APIResponse(responseCode = "416", ref = SharedRefusalsFilter.RANGE_NOT_SATISFIABLE)
    fun downloadExport(id: UUID, @HeaderParam("Range") rangeHeader: String?): RestResponse<StreamingOutput> {
        val user = securityIdentity.getUser()
        // Opened at 0 always: the size needed to parse the Range header is only known once the
        // export row is read, and this use case is the single validated source for it (spec §5).
        val opened = downloader.open(user, id, 0)
        val range = RangeHeader.parse(rangeHeader, opened.totalByteSize)
        return ByteRangeResponse.builder(opened.stream, opened.totalByteSize, range)
            .header("Content-Type", opened.mediaType)
            .header("ETag", "\"${opened.sha256}\"")
            .header("Content-Disposition", contentDispositionHeader(opened, user))
            .build()
    }

    @DELETE
    @Path("/{id}")
    @APIResponse(responseCode = "204", description = "Export deleted, its archive released")
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.EXPORT_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.EXPORT_NOT_FOUND)
    fun deleteExport(id: UUID): RestResponse<Void> {
        val user = securityIdentity.getUser()
        deleter.delete(user, id)
        return RestResponse.noContent()
    }

    private fun contentDispositionHeader(opened: OpenedExport, user: User): String {
        val extension = opened.fileExtension
        val rawName = "${isoDate(opened.completedAt)}-pinry-export-${user.name}.$extension"
        val fallback = "$FALLBACK_FILE_STEM.$extension"
        return ContentDispositionFileName.headerValue(rawName, fallback)
    }

    private fun isoDate(instant: Instant): String = instant.toString().take(ISO_DATE_LENGTH)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        private const val ISO_DATE_LENGTH = 10
        private const val FALLBACK_FILE_STEM = "export"

        private const val ARCHIVE_MEDIA_TYPE = "application/zip"
    }
}
