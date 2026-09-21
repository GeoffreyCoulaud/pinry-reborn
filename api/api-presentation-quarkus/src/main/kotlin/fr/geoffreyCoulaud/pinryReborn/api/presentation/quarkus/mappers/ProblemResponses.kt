package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo

/** Builds every RFC 7807 payload the mappers answer. Callers may add headers before build(). */
object ProblemResponses {
    const val PROBLEM_JSON_MEDIA_TYPE = "application/problem+json"

    /**
     * What every batch route publishes for its refused body, the five reading as one grammar
     * (`docs/adr/0039-a-batch-route-is-all-or-nothing.md`, decision 3).
     */
    const val BATCH_BODY_REFUSED = "The body is missing, or its list of identifiers is empty"

    /** What every route taking a `q` publishes for a term that is present and blank, the three reading as one. */
    const val BLANK_QUERY_REFUSED = "The q parameter is present and blank"

    /** RFC 7807 challenge value: opaque bearer token, no realm. */
    const val WWW_AUTHENTICATE_BEARER = "Bearer"

    fun problemResponse(
        status: Response.Status,
        detail: String?,
        code: String,
        uriInfo: UriInfo,
    ): Response.ResponseBuilder = problemResponse(status.statusCode, status.reasonPhrase, detail, code, uriInfo)

    /** For a raw status code, since not every mapped status has a [Response.Status] constant (422, 507). */
    @Suppress("LongParameterList")
    fun problemResponse(
        status: Int,
        title: String,
        detail: String?,
        code: String,
        uriInfo: UriInfo,
        currentLength: Long? = null,
    ): Response.ResponseBuilder =
        Response
            .status(status)
            .entity(
                ProblemDetail(
                    title = title,
                    status = status,
                    detail = detail,
                    instance = uriInfo.path,
                    code = code,
                    currentLength = currentLength,
                ),
            )
            .type(PROBLEM_JSON_MEDIA_TYPE)

    /** The `400` for a body Jackson refused. The detail says where, never which class Jackson was binding to. */
    fun malformedBody(exception: JsonProcessingException, uriInfo: UriInfo): Response.ResponseBuilder =
        problemResponse(
            status = Response.Status.BAD_REQUEST,
            detail = malformedBodyDetail(exception),
            code = FrameworkErrorCode.MALFORMED_BODY.name,
            uriInfo = uriInfo,
        )

    /** The `500` with no detail: an unmapped throwable's message is nobody's to publish. */
    fun internalError(uriInfo: UriInfo): Response.ResponseBuilder =
        problemResponse(
            status = Response.Status.INTERNAL_SERVER_ERROR,
            detail = null,
            code = FrameworkErrorCode.INTERNAL_ERROR.name,
            uriInfo = uriInfo,
        )

    private fun malformedBodyDetail(exception: JsonProcessingException): String {
        if (exception !is JsonMappingException) return "The body is not valid JSON: ${exception.originalMessage}"
        val path = propertyPath(exception)
        return "The body could not be read at ${if (path.isEmpty()) "its root" else "`$path`"}"
    }

    // `description`, `tags[2]`: the path Jackson was reading, without the class it was binding to.
    private fun propertyPath(exception: JsonMappingException): String =
        exception.path
            .joinToString(separator = "") { reference ->
                if (reference.fieldName != null) ".${reference.fieldName}" else "[${reference.index}]"
            }
            .removePrefix(".")
}
