package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinDuplicateUpdateInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinDuplicateListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinDuplicateOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON as JSON
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse
import java.util.UUID

/** A pin's likely duplicates, which the worker found (ADR 0051). */
@Path("/api/v1/pins")
class PinDuplicateController(
    private val pinDuplicates: PinDuplicates,
    private val securityIdentity: SecurityIdentity,
    private val pinResponses: PinResponses,
) {
    @GET
    @Authenticated
    @Path("/{pinId}/duplicates")
    @Operation(
        summary = "List the pin's likely duplicates",
        description = "Those the user rejected included. A pair is listed while both pins are active, so a " +
            "recycled pin lists none.",
    )
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = JSON, schema = Schema(implementation = PinDuplicateListOutputDto::class))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.PIN_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.PIN_NOT_FOUND)
    fun listDuplicates(pinId: UUID): RestResponse<PinDuplicateListOutputDto> {
        val user = securityIdentity.getUser()
        return RestResponse.ok(pinResponses.duplicates(pinDuplicates.list(pinId = pinId, user = user)))
    }

    @PUT
    @Authenticated
    @Path("/{pinId}/duplicates/{otherPinId}")
    @Operation(summary = "Reject a likely duplicate, or take the rejection back")
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = JSON, schema = Schema(implementation = PinDuplicateOutputDto::class))])
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BODY)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.PIN_FORBIDDEN)
    @APIResponse(responseCode = "404",
        description = "The pin does not exist, the two pins are not a listed pair, or a path value could not be read",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["PIN_DOES_NOT_EXIST", "DUPLICATE_DOES_NOT_EXIST", "UNKNOWN_ROUTE"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun updateDuplicate(
        pinId: UUID,
        otherPinId: UUID,
        @Valid @NotNull dto: PinDuplicateUpdateInputDto,
    ): RestResponse<PinDuplicateOutputDto> {
        val user = securityIdentity.getUser()
        // Never null here: validation refused a missing one.
        val duplicate = pinDuplicates.setRejected(pinId, otherPinId, rejected = dto.rejected == true, user)
        return RestResponse.ok(pinResponses.duplicate(duplicate))
    }
}
