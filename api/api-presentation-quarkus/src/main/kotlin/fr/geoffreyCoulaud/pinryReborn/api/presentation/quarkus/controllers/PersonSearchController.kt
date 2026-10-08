package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PersonSearchOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.SearchResultMapper.toPersonSearchDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonSearcher
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

/** The tag search over the user's persons, with its bounds and refusals. */
@Path("/api/v1/persons")
class PersonSearchController(
    private val personSearcher: PersonSearcher,
    private val securityIdentity: SecurityIdentity,
) {
    @GET
    @Authenticated
    @Path("/search")
    @APIResponse(
        responseCode = "200",
        description = "OK",
        content =
            [
                Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = Schema(implementation = PersonSearchOutputDto::class),
                )
            ],
    )
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.BLANK_QUERY)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.UNREADABLE_QUERY)
    fun searchPersons(
        @QueryParam("q") query: String? = null,
        @QueryParam("limit") limitParam: Int? = null,
    ): RestResponse<PersonSearchOutputDto> {
        val user = securityIdentity.getUser()
        val limit =
            (limitParam ?: TagSearchController.DEFAULT_LIMIT).coerceIn(
                TagSearchController.MIN_LIMIT,
                TagSearchController.MAX_LIMIT,
            )

        return RestResponse.ok(
            personSearcher.searchPersons(user = user, query = query.orEmpty(), limit = limit).toPersonSearchDto()
        )
    }
}
