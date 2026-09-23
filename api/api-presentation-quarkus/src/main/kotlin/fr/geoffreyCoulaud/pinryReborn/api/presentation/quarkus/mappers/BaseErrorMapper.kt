package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.BaseError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ErrorCode
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImportChunkOffsetMismatchError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ThrottledError
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.GONE
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.REQUEST_ENTITY_TOO_LARGE
import jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class BaseErrorMapper : ExceptionMapper<BaseError> {
    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: BaseError): Response {
        val (code, status) = problemFor(exception.code)
        val resolvedStatus = Response.Status.fromStatusCode(status)
        // A status with no Response.Status constant takes its title from the table below. `getValue`
        // rather than a default: a new such status is a missing entry, not a wrong title to ship.
        val title =
            if (resolvedStatus == null) TITLES_WITHOUT_CONSTANT.getValue(status) else resolvedStatus.reasonPhrase
        // The one refusal a client acts on with a number: it resumes from this length rather than
        // parsing it out of the sentence that also names it.
        val currentLength = (exception as? ImportChunkOffsetMismatchError)?.currentLength
        val builder = ProblemResponses.problemResponse(
            status = status,
            title = title,
            detail = exception.message,
            code = code,
            uriInfo = uriInfo,
            currentLength = currentLength,
        )
        if (exception is ThrottledError) {
            builder.header("Retry-After", exception.retryAfterSeconds)
        }
        return builder.build()
    }

    /** The wire code and the raw HTTP status, since not every mapped status has a [Response.Status] constant. */
    // Flat one-arm-per-ErrorCode dispatch table, not nested branching; the exhaustive `when` (no
    // `else`) is intentional so a future or renamed ErrorCode fails to compile here.
    @Suppress("CyclomaticComplexMethod")
    private fun problemFor(code: ErrorCode): Pair<ProblemCode, Int> =
        when (code) {
            ErrorCode.USERNAME_ALREADY_EXISTS -> ProblemCode.USERNAME_ALREADY_EXISTS to CONFLICT.statusCode
            ErrorCode.PIN_DOES_NOT_EXIST -> ProblemCode.PIN_DOES_NOT_EXIST to NOT_FOUND.statusCode
            ErrorCode.PIN_INSUFFICIENT_PERMISSIONS -> ProblemCode.PIN_INSUFFICIENT_PERMISSIONS to FORBIDDEN.statusCode
            ErrorCode.PIN_NOT_SOFT_DELETED -> ProblemCode.PIN_NOT_SOFT_DELETED to CONFLICT.statusCode
            ErrorCode.PIN_ALREADY_SOFT_DELETED -> ProblemCode.PIN_ALREADY_SOFT_DELETED to CONFLICT.statusCode
            ErrorCode.SEARCH_EMPTY_QUERY -> ProblemCode.SEARCH_EMPTY_QUERY to BAD_REQUEST.statusCode
            ErrorCode.USER_DOES_NOT_EXIST -> ProblemCode.USER_DOES_NOT_EXIST to UNAUTHORIZED.statusCode
            ErrorCode.INVALID_PASSWORD -> ProblemCode.INVALID_PASSWORD to UNAUTHORIZED.statusCode
            ErrorCode.INVALID_HTTP_AUTHORIZATION_SCHEME ->
                ProblemCode.INVALID_HTTP_AUTHORIZATION_SCHEME to UNAUTHORIZED.statusCode
            ErrorCode.IMAGE_DOES_NOT_EXIST -> ProblemCode.IMAGE_DOES_NOT_EXIST to NOT_FOUND.statusCode
            ErrorCode.IMAGE_INSUFFICIENT_PERMISSIONS ->
                ProblemCode.IMAGE_INSUFFICIENT_PERMISSIONS to FORBIDDEN.statusCode
            ErrorCode.IMAGE_TOO_LARGE -> ProblemCode.IMAGE_TOO_LARGE to REQUEST_ENTITY_TOO_LARGE.statusCode
            ErrorCode.IMAGE_INVALID -> ProblemCode.IMAGE_INVALID to UNPROCESSABLE_ENTITY_STATUS_CODE
            ErrorCode.IMAGE_SOURCE_URL_INVALID -> ProblemCode.IMAGE_SOURCE_URL_INVALID to BAD_REQUEST.statusCode
            ErrorCode.IMAGE_DOWNLOAD_IN_PROGRESS -> ProblemCode.IMAGE_DOWNLOAD_IN_PROGRESS to CONFLICT.statusCode
            ErrorCode.IMAGE_RENDITION_SIZE_INVALID -> ProblemCode.IMAGE_RENDITION_SIZE_INVALID to BAD_REQUEST.statusCode
            ErrorCode.BOARD_DOES_NOT_EXIST -> ProblemCode.BOARD_DOES_NOT_EXIST to NOT_FOUND.statusCode
            ErrorCode.BOARD_INSUFFICIENT_PERMISSIONS ->
                ProblemCode.BOARD_INSUFFICIENT_PERMISSIONS to FORBIDDEN.statusCode
            ErrorCode.BOARD_NOT_SOFT_DELETED -> ProblemCode.BOARD_NOT_SOFT_DELETED to CONFLICT.statusCode
            ErrorCode.BOARD_ALREADY_SOFT_DELETED -> ProblemCode.BOARD_ALREADY_SOFT_DELETED to CONFLICT.statusCode
            ErrorCode.BOARD_NAME_ALREADY_EXISTS -> ProblemCode.BOARD_NAME_ALREADY_EXISTS to CONFLICT.statusCode
            ErrorCode.REAUTHENTICATION_FAILED -> ProblemCode.REAUTHENTICATION_FAILED to FORBIDDEN.statusCode
            ErrorCode.PASSWORD_PREVIOUSLY_USED ->
                ProblemCode.PASSWORD_PREVIOUSLY_USED to UNPROCESSABLE_ENTITY_STATUS_CODE
            ErrorCode.PASSWORD_CHANGED_TOO_SOON -> ProblemCode.PASSWORD_CHANGED_TOO_SOON to TOO_MANY_REQUESTS.statusCode
            ErrorCode.PASSWORD_CHANGE_COLLISION -> ProblemCode.PASSWORD_CHANGE_COLLISION to CONFLICT.statusCode
            ErrorCode.UNSUPPORTED_REAUTHENTICATION_FACTOR ->
                ProblemCode.UNSUPPORTED_REAUTHENTICATION_FACTOR to BAD_REQUEST.statusCode
            ErrorCode.EXPORT_ALREADY_IN_PROGRESS -> ProblemCode.EXPORT_ALREADY_IN_PROGRESS to CONFLICT.statusCode
            ErrorCode.EXPORT_TOO_SOON -> ProblemCode.EXPORT_TOO_SOON to TOO_MANY_REQUESTS.statusCode
            ErrorCode.EXPORT_DOES_NOT_EXIST -> ProblemCode.EXPORT_DOES_NOT_EXIST to NOT_FOUND.statusCode
            ErrorCode.EXPORT_INSUFFICIENT_PERMISSIONS ->
                ProblemCode.EXPORT_INSUFFICIENT_PERMISSIONS to FORBIDDEN.statusCode
            ErrorCode.EXPORT_NOT_READY -> ProblemCode.EXPORT_NOT_READY to CONFLICT.statusCode
            ErrorCode.EXPORT_GONE -> ProblemCode.EXPORT_GONE to GONE.statusCode
            ErrorCode.IMPORT_ALREADY_IN_PROGRESS -> ProblemCode.IMPORT_ALREADY_IN_PROGRESS to CONFLICT.statusCode
            ErrorCode.IMPORT_DOES_NOT_EXIST -> ProblemCode.IMPORT_DOES_NOT_EXIST to NOT_FOUND.statusCode
            ErrorCode.IMPORT_INSUFFICIENT_PERMISSIONS ->
                ProblemCode.IMPORT_INSUFFICIENT_PERMISSIONS to FORBIDDEN.statusCode
            ErrorCode.IMPORT_NOT_AWAITING_ARCHIVE -> ProblemCode.IMPORT_NOT_AWAITING_ARCHIVE to CONFLICT.statusCode
            ErrorCode.IMPORT_CHUNK_OFFSET_MISMATCH -> ProblemCode.IMPORT_CHUNK_OFFSET_MISMATCH to CONFLICT.statusCode
            ErrorCode.IMPORT_ARCHIVE_EMPTY -> ProblemCode.IMPORT_ARCHIVE_EMPTY to CONFLICT.statusCode
            ErrorCode.IMPORT_ARCHIVE_TOO_LARGE ->
                ProblemCode.IMPORT_ARCHIVE_TOO_LARGE to REQUEST_ENTITY_TOO_LARGE.statusCode
            ErrorCode.IMPORT_INSUFFICIENT_STORAGE ->
                ProblemCode.IMPORT_INSUFFICIENT_STORAGE to INSUFFICIENT_STORAGE_STATUS_CODE
            ErrorCode.TOO_MANY_AUTHENTICATION_ATTEMPTS ->
                ProblemCode.TOO_MANY_AUTHENTICATION_ATTEMPTS to TOO_MANY_REQUESTS.statusCode
        }

    private companion object {
        // jakarta.ws.rs 4.0's Response.Status stops at NETWORK_AUTHENTICATION_REQUIRED, so it holds
        // neither of these (RFC 9110 422, RFC 4918 507).
        const val UNPROCESSABLE_ENTITY_STATUS_CODE = 422
        const val INSUFFICIENT_STORAGE_STATUS_CODE = 507

        val TITLES_WITHOUT_CONSTANT =
            mapOf(
                UNPROCESSABLE_ENTITY_STATUS_CODE to "Unprocessable Entity",
                INSUFFICIENT_STORAGE_STATUS_CODE to "Insufficient Storage",
            )
    }
}
