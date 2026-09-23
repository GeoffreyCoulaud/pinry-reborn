package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.TagSearchOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.SearchResultMapper.toTagSearchDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.TagSearcher
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse

@Path("/api/v1/tags")
class TagSearchController(
    private val tagSearcher: TagSearcher,
    private val securityIdentity: SecurityIdentity,
) {
    @GET
    @Authenticated
    @Path("/search")
    // SmallRye stops generating the success response as soon as an operation declares one of its own,
    // so the 200 is written out beside the 400 rather than dropped from the contract.
    @APIResponse(
        responseCode = "200",
        description = "OK",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = TagSearchOutputDto::class),
            ),
        ],
    )
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.BLANK_QUERY)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.UNREADABLE_QUERY)
    fun searchTags(
        @QueryParam("q") query: String?,
        @QueryParam("limit") limitParam: Int?,
    ): RestResponse<TagSearchOutputDto> {
        val user = securityIdentity.getUser()
        // Bounded on both sides, as the catalogue's pageSize is: a non-positive row bound is no bound at all.
        val limit = (limitParam ?: DEFAULT_LIMIT).coerceIn(MIN_LIMIT, MAX_LIMIT)

        return tagSearcher
            .searchTags(user = user, query = query.orEmpty(), limit = limit)
            .toTagSearchDto()
            .let { RestResponse.ok(it) }
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 20
    }
}
