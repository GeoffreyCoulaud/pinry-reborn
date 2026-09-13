package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import io.quarkus.smallrye.openapi.OpenApiFilter
import org.eclipse.microprofile.openapi.OASFactory
import org.eclipse.microprofile.openapi.OASFilter
import org.eclipse.microprofile.openapi.models.Operation

/**
 * A protected operation accepts either transport, and SmallRye stamps exactly one scheme name on it,
 * the first of the two declared. This replaces that lone requirement with both, in a fixed order.
 */
@OpenApiFilter(stages = [OpenApiFilter.RunStage.BUILD])
class SessionSecurityRequirementFilter : OASFilter {
    override fun filterOperation(operation: Operation): Operation {
        // An operation SmallRye left unprotected is one @PermitAll declared, and it stays open.
        if (operation.security.isNullOrEmpty()) {
            return operation
        }
        operation.security = SCHEMES.map { OASFactory.createSecurityRequirement().addScheme(it) }
        return operation
    }

    companion object {
        const val BEARER_SCHEME = "BearerScheme"
        const val COOKIE_SCHEME = "CookieScheme"
        private val SCHEMES = listOf(BEARER_SCHEME, COOKIE_SCHEME)
    }
}
