package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http

import com.fasterxml.jackson.databind.ObjectMapper
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemCode
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE
import io.quarkus.runtime.configuration.MemorySize
import io.quarkus.vertx.http.runtime.RouteConstants
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * The `413` for a body declared past `quarkus.http.limits.max-body-size`, in the problem format: Quarkus's own
 * route, at `ROUTE_ORDER_UPLOAD_LIMIT`, ends a bodyless one before any mapper sees it, so this runs just ahead.
 */
@ApplicationScoped
class OversizeBodyRefusal(
    private val objectMapper: ObjectMapper,
    @param:ConfigProperty(name = "quarkus.http.limits.max-body-size") private val maxBodySize: MemorySize,
) {
    fun register(@Observes router: Router) {
        router.route().order(RouteConstants.ROUTE_ORDER_UPLOAD_LIMIT - 1).handler(::handle)
    }

    fun handle(context: RoutingContext) {
        val declaredLength = context.request().getHeader(HttpHeaders.CONTENT_LENGTH)?.toLongOrNull()
        if (declaredLength == null || declaredLength <= maxBodySize.asLongValue()) return context.next()
        val status = Response.Status.REQUEST_ENTITY_TOO_LARGE
        val problem = ProblemDetail(
            title = status.reasonPhrase,
            status = status.statusCode,
            detail = "The body is longer than the ${maxBodySize.asLongValue()} bytes this server reads",
            instance = context.normalizedPath(),
            code = ProblemCode.BODY_TOO_LARGE.name,
        )
        // The body is never read, so the connection cannot carry another request: Quarkus's own refusal closes it too.
        context.response()
            .setStatusCode(status.statusCode)
            .putHeader(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON_MEDIA_TYPE)
            .putHeader(HttpHeaders.CONNECTION, HttpHeaders.CLOSE)
            .endHandler { context.request().connection().close() }
            .end(objectMapper.writeValueAsString(problem))
    }
}
