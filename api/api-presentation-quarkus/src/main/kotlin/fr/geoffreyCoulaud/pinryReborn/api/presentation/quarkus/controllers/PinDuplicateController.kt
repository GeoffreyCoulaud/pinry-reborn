package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinDuplicateResolutionInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinMergeInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinDuplicateListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.DuplicateDecisionMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.DuplicateResolver
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinMerger
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON as JSON
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse
import java.util.UUID

/** A pin's likely duplicates, which the worker found, and their merge (ADR 0051). */
@Path("/api/v1/pins")
class PinDuplicateController(
    private val pinDuplicates: PinDuplicates,
    private val pinMerger: PinMerger,
    private val duplicateResolver: DuplicateResolver,
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

    @POST
    @Authenticated
    @Path("/{pinId}/duplicates/resolutions")
    @Operation(
        summary = "Apply a decision to every version of the pin's group of duplicates, all or nothing",
        description = "A rejected pin's pairs with the kept and merged pins are rejected first. The kept pin " +
            "gains the merged pins' boards and tags, and fills a blank description or page address from the " +
            "oldest that has one; they go to the recycle bin. A duplicate the body does not name is left as is.",
    )
    @APIResponse(responseCode = "200", description = "The kept pin",
        content = [Content(mediaType = JSON, schema = Schema(implementation = PinOutputDto::class))])
    @APIResponse(responseCode = "400",
        description = "The body is not JSON, a key is not a pin id, a value is not a decision, there are too " +
            "many entries, not exactly one pin is kept, the open pin is not named or is rejected, or no other " +
            "pin is named",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["VALIDATION_ERROR", "MALFORMED_BODY"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.PIN_FORBIDDEN)
    @APIResponse(responseCode = "404",
        description = "The pin does not exist, a named pin is not among its listed duplicates, or a path value " +
            "could not be read",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["PIN_DOES_NOT_EXIST", "DUPLICATE_DOES_NOT_EXIST", "UNKNOWN_ROUTE"])]))])
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.PIN_ALREADY_RECYCLED)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun resolveDuplicates(
        pinId: UUID,
        @Valid @NotNull dto: PinDuplicateResolutionInputDto,
    ): RestResponse<PinOutputDto> {
        val user = securityIdentity.getUser()
        val decisions = dto.decisions.mapValues { (_, decision) -> decision.toDomain() }
        return RestResponse.ok(pinResponses.pin(duplicateResolver.resolve(pinId, decisions, user)))
    }

    @POST
    @Authenticated
    @Path("/merges")
    @Operation(
        summary = "Merge pins into the kept one, all or nothing",
        description = "The kept pin keeps its media, its description and its sources, and gains every board " +
            "and tag of the absorbed pins. A blank description and a missing page address are filled from " +
            "the first absorbed pin, in the list's order, that has one. The absorbed pins go to the recycle " +
            "bin, and their likely duplicates are not carried over.",
    )
    @APIResponse(responseCode = "200", description = "The kept pin, merged",
        content = [Content(mediaType = JSON, schema = Schema(implementation = PinOutputDto::class))])
    @APIResponse(responseCode = "400",
        description = "The body is missing, or the absorbed list is empty, too long, names a pin twice or " +
            "names the kept pin",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["VALIDATION_ERROR", "MALFORMED_BODY"])]))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.PIN_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.PIN_IN_BODY_NOT_FOUND)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.PIN_ALREADY_RECYCLED)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun mergePins(@Valid @NotNull dto: PinMergeInputDto): RestResponse<PinOutputDto> {
        val user = securityIdentity.getUser()
        val kept = pinMerger.merge(keptPinId = dto.keptPinId, absorbedPinIds = dto.absorbedPinIds, user = user)
        return RestResponse.ok(pinResponses.pin(kept))
    }
}
