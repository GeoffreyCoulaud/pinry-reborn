package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemCode
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
        const val UNSUPPORTED_REAUTHENTICATION_FACTOR = "UnsupportedReauthenticationFactor"
        const val TOO_MANY_AUTHENTICATION_ATTEMPTS = "TooManyAuthenticationAttempts"

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
            UNSUPPORTED_REAUTHENTICATION_FACTOR to refusal(
                "The X-Reauthentication header is missing or names no factor the route accepts",
                ProblemCode.UNSUPPORTED_REAUTHENTICATION_FACTOR,
            ),
            TOO_MANY_AUTHENTICATION_ATTEMPTS to refusal(
                "The attempt limiter holds this account closed; Retry-After says for how long",
                ProblemCode.TOO_MANY_AUTHENTICATION_ATTEMPTS,
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
