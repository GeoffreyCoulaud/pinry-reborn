package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemCode
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.BATCH_BODY_REFUSED
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.BLANK_QUERY_REFUSED
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE
import io.quarkus.smallrye.openapi.OpenApiFilter
import org.eclipse.microprofile.openapi.OASFactory
import org.eclipse.microprofile.openapi.OASFilter
import org.eclipse.microprofile.openapi.models.OpenAPI
import org.eclipse.microprofile.openapi.models.Operation
import org.eclipse.microprofile.openapi.models.responses.APIResponse

/**
 * The refusals several operations declare identically, under `components.responses`, which a route
 * names with `@APIResponse(ref = ...)` (`docs/adr/0042-the-presentation-owns-the-refusal-codes.md`).
 */
@OpenApiFilter(stages = [OpenApiFilter.RunStage.BUILD])
class SharedRefusalsFilter : OASFilter {
    override fun filterOperation(operation: Operation): Operation {
        // SmallRye's own 403 on a protected operation carries no body, and no role check here refuses one.
        operation.responses.getAPIResponse("403")
            ?.takeIf { it.content == null && it.ref == null }
            ?.let { operation.responses.removeAPIResponse("403") }
        // Protected is what SmallRye stamped a requirement on, as SessionSecurityRequirementFilter reads it.
        if (!operation.security.isNullOrEmpty()) {
            operation.responses.addAPIResponse("401", OASFactory.createAPIResponse().ref(UNAUTHENTICATED))
        }
        return operation
    }

    override fun filterOpenAPI(openAPI: OpenAPI) {
        if (openAPI.components == null) openAPI.components = OASFactory.createComponents()
        SHARED.forEach { (name, response) -> openAPI.components.addResponse(name, response) }
    }

    companion object {
        const val UNAUTHENTICATED = "Unauthenticated"
        const val INVALID_BODY = "InvalidBody"
        const val UNSUPPORTED_MEDIA_TYPE = "UnsupportedMediaType"
        const val REAUTHENTICATION_FAILED = "ReauthenticationFailed"
        const val REAUTHENTICATION_HEADER_FAILED = "ReauthenticationHeaderFailed"
        const val UNSUPPORTED_REAUTHENTICATION_FACTOR = "UnsupportedReauthenticationFactor"
        const val TOO_MANY_AUTHENTICATION_ATTEMPTS = "TooManyAuthenticationAttempts"
        const val INVALID_BATCH_BODY = "InvalidBatchBody"
        const val BLANK_QUERY = "BlankQuery"
        const val UNREADABLE_QUERY = "UnreadableQuery"
        const val PIN_FORBIDDEN = "PinForbidden"
        const val PIN_NOT_FOUND = "PinNotFound"
        const val PIN_ALREADY_RECYCLED = "PinAlreadyRecycled"
        const val PIN_IN_BODY_NOT_FOUND = "PinInBodyNotFound"
        const val PIN_NOT_RECYCLED = "PinNotRecycled"
        const val BOARD_FORBIDDEN = "BoardForbidden"
        const val BOARD_NOT_FOUND = "BoardNotFound"
        const val BOARD_NOT_RECYCLED = "BoardNotRecycled"
        const val BOARD_NAME_TAKEN = "BoardNameTaken"
        const val BOARD_OR_PIN_FORBIDDEN = "BoardOrPinForbidden"
        const val BOARD_OR_PIN_NOT_FOUND = "BoardOrPinNotFound"
        const val IMAGE_FORBIDDEN = "ImageForbidden"
        const val IMAGE_NOT_FOUND = "ImageNotFound"
        const val EXPORT_FORBIDDEN = "ExportForbidden"
        const val EXPORT_NOT_FOUND = "ExportNotFound"
        const val IMPORT_FORBIDDEN = "ImportForbidden"
        const val IMPORT_NOT_FOUND = "ImportNotFound"

        private val SHARED = mapOf(
            UNAUTHENTICATED to refusal(
                "No session, or its token is unknown, revoked or expired",
                ProblemCode.AUTHENTICATION_REQUIRED,
                ProblemCode.AUTHENTICATION_FAILED,
                ProblemCode.SESSION_EXPIRED,
            ),
            INVALID_BODY to refusal(
                "The body is not JSON, or a field breaks its constraint",
                ProblemCode.VALIDATION_ERROR,
                ProblemCode.MALFORMED_BODY,
            ),
            UNSUPPORTED_MEDIA_TYPE to refusal(
                "The route does not read this Content-Type",
                ProblemCode.UNSUPPORTED_MEDIA_TYPE,
            ),
            REAUTHENTICATION_FAILED to refusal(
                "The current password is wrong",
                ProblemCode.REAUTHENTICATION_FAILED,
            ),
            REAUTHENTICATION_HEADER_FAILED to refusal(
                "The X-Reauthentication header is absent, or the password it carries is wrong",
                ProblemCode.REAUTHENTICATION_FAILED,
            ),
            UNSUPPORTED_REAUTHENTICATION_FACTOR to refusal(
                "The X-Reauthentication header names no factor the route accepts",
                ProblemCode.UNSUPPORTED_REAUTHENTICATION_FACTOR,
            ),
            TOO_MANY_AUTHENTICATION_ATTEMPTS to refusal(
                "The attempt limiter holds this account closed; Retry-After says for how long",
                ProblemCode.TOO_MANY_AUTHENTICATION_ATTEMPTS,
            ),
            INVALID_BATCH_BODY to refusal(
                BATCH_BODY_REFUSED,
                ProblemCode.VALIDATION_ERROR,
                ProblemCode.MALFORMED_BODY,
            ),
            BLANK_QUERY to refusal(BLANK_QUERY_REFUSED, ProblemCode.SEARCH_EMPTY_QUERY),
            UNREADABLE_QUERY to refusal(
                "A query value could not be read",
                ProblemCode.UNKNOWN_ROUTE,
            ),
            PIN_FORBIDDEN to refusal(
                "A pin the request names belongs to another account",
                ProblemCode.PIN_INSUFFICIENT_PERMISSIONS,
            ),
            PIN_NOT_FOUND to refusal(
                "A pin the request names does not exist, or a path or query value could not be read",
                ProblemCode.PIN_DOES_NOT_EXIST,
                ProblemCode.UNKNOWN_ROUTE,
            ),
            PIN_ALREADY_RECYCLED to refusal(
                "The pin is in the recycle bin",
                ProblemCode.PIN_ALREADY_SOFT_DELETED,
            ),
            PIN_IN_BODY_NOT_FOUND to refusal(
                "A pin the body names does not exist",
                ProblemCode.PIN_DOES_NOT_EXIST,
            ),
            PIN_NOT_RECYCLED to refusal(
                "The pin is not in the recycle bin",
                ProblemCode.PIN_NOT_SOFT_DELETED,
            ),
            BOARD_FORBIDDEN to refusal(
                "A board the request names belongs to another account",
                ProblemCode.BOARD_INSUFFICIENT_PERMISSIONS,
            ),
            BOARD_NOT_FOUND to refusal(
                "The board does not exist, or a path or query value could not be read",
                ProblemCode.BOARD_DOES_NOT_EXIST,
                ProblemCode.UNKNOWN_ROUTE,
            ),
            BOARD_NOT_RECYCLED to refusal(
                "The board is not in the recycle bin",
                ProblemCode.BOARD_NOT_SOFT_DELETED,
            ),
            BOARD_NAME_TAKEN to refusal(
                "This account already holds a board of that name, ASCII case folded, and a recycled " +
                    "board holds its name until the bin is emptied",
                ProblemCode.BOARD_NAME_ALREADY_EXISTS,
            ),
            BOARD_OR_PIN_FORBIDDEN to refusal(
                "The board, or a pin the body names, belongs to another account",
                ProblemCode.BOARD_INSUFFICIENT_PERMISSIONS,
                ProblemCode.PIN_INSUFFICIENT_PERMISSIONS,
            ),
            BOARD_OR_PIN_NOT_FOUND to refusal(
                "The board, or a pin the body names, does not exist, or a path value could not be read",
                ProblemCode.BOARD_DOES_NOT_EXIST,
                ProblemCode.PIN_DOES_NOT_EXIST,
                ProblemCode.UNKNOWN_ROUTE,
            ),
            IMAGE_FORBIDDEN to refusal(
                "The pin belongs to another account",
                ProblemCode.IMAGE_INSUFFICIENT_PERMISSIONS,
            ),
            IMAGE_NOT_FOUND to refusal(
                "The pin, its image or its download does not exist, or a path or query value could not be read",
                ProblemCode.IMAGE_DOES_NOT_EXIST,
                ProblemCode.UNKNOWN_ROUTE,
            ),
            EXPORT_FORBIDDEN to refusal(
                "The export belongs to another account",
                ProblemCode.EXPORT_INSUFFICIENT_PERMISSIONS,
            ),
            EXPORT_NOT_FOUND to refusal(
                "The export does not exist, or a path value could not be read",
                ProblemCode.EXPORT_DOES_NOT_EXIST,
                ProblemCode.UNKNOWN_ROUTE,
            ),
            IMPORT_FORBIDDEN to refusal(
                "The import belongs to another account",
                ProblemCode.IMPORT_INSUFFICIENT_PERMISSIONS,
            ),
            IMPORT_NOT_FOUND to refusal(
                "The import does not exist, or a path or query value could not be read",
                ProblemCode.IMPORT_DOES_NOT_EXIST,
                ProblemCode.UNKNOWN_ROUTE,
            ),
        )

        private fun refusal(description: String, vararg codes: ProblemCode): APIResponse {
            val schema = OASFactory.createSchema()
                .addAllOf(OASFactory.createSchema().ref("ProblemDetail"))
                .addProperty("code", OASFactory.createSchema().enumeration(codes.map { it.name }))
            return OASFactory.createAPIResponse()
                .description(description)
                .content(
                    OASFactory.createContent()
                        .addMediaType(PROBLEM_JSON_MEDIA_TYPE, OASFactory.createMediaType().schema(schema)),
                )
        }
    }
}
