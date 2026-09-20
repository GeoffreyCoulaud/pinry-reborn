package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinCreationInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinSortStrategyInputEnum
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinUpdateInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinSortStrategyMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.serialization.Base64Json
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinRecycleBin
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinUpdater
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder
import java.net.URI
import java.util.UUID

@Path("/api/v1/pins")
class PinController(
    private val pinCreator: PinCreator,
    private val pinGetter: PinGetter,
    private val pinRecycleBin: PinRecycleBin,
    private val pinUpdater: PinUpdater,
    private val securityIdentity: SecurityIdentity,
    private val pinResponses: PinResponses,
) {
    @GET
    @Authenticated
    @Path("/{pinId}")
    fun getPin(pinId: UUID): RestResponse<PinOutputDto> {
        val user = securityIdentity.getUser()
        return pinGetter
            .getPinForUser(pinId = pinId, reader = user)
            .let { RestResponse.ok(pinResponses.pin(it)) }
    }

    @POST
    @Authenticated
    // SmallRye reads the status off the return type, and a runtime ResponseBuilder carries none,
    // so the 201 this route answers is declared here as BoardController's creation declares its own.
    @APIResponse(
        responseCode = "201",
        description = "Pin created",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = Schema(implementation = PinOutputDto::class),
            ),
        ],
    )
    fun createPin(@Valid creationDto: PinCreationInputDto): RestResponse<PinOutputDto> {
        val author = securityIdentity.getUser()
        val pin = pinCreator.createPin(
            author = author,
            sourceContextUrl = creationDto.sourceContextUrl.blankAsNone(),
            sourceMediaUrl = creationDto.sourceMediaUrl.blankAsNone(),
            description = creationDto.description,
            tags = emptyList(),
        )
        return ResponseBuilder
            .created<PinOutputDto>(URI("/api/v1/pins/${pin.id}"))
            .entity(pinResponses.pin(pin))
            .build()
    }

    @GET
    @Authenticated
    fun listPins(
        @QueryParam("cursor") @Base64Json cursorInput: CursorDto? = null,
        @QueryParam("pageSize") pageSizeInput: Int? = null,
        @QueryParam("sort") sortInput: PinSortStrategyInputEnum? = null,
    ): RestResponse<PinListOutputDto> {
        val user = securityIdentity.getUser()
        val pageSize = pageSizeInput ?: DEFAULT_PAGE_SIZE
        val sort = if (sortInput != null) sortInput.toDomain() else PinSortStrategy.CREATED_AT_ASC
        val cursor = cursorInput?.let { cursorInput.toDomain() }

        return pinGetter
            .listPinsPaginatedForUser(reader = user, cursor = cursor, pageSize = pageSize, sort = sort)
            .let { RestResponse.ok(pinResponses.page(it)) }
    }

    @DELETE
    @Authenticated
    @Path("/{pinId}")
    fun softDeletePin(pinId: UUID): RestResponse<Void> {
        val user = securityIdentity.getUser()
        pinRecycleBin.softDelete(pinId = pinId, user = user)
        return RestResponse.noContent()
    }

    @PUT
    @Authenticated
    @Path("/{pinId}")
    @Operation(
        summary = "Replace the pin",
        description = "Every field is replaced by what is sent, so an unchanged field is sent as it was " +
            "read and an empty list clears. A tag name is an identity per author under an ASCII fold: " +
            "`Landscape` and `landscape` are one tag, and the response carries the stored spelling, not " +
            "the one sent. The fold covers A to Z only, so `ÉTÉ` and `été` stay two tags.",
    )
    fun updatePin(pinId: UUID, @Valid updateDto: PinUpdateInputDto): RestResponse<PinOutputDto> {
        val user = securityIdentity.getUser()
        return pinUpdater
            .update(
                pinId = pinId,
                description = updateDto.description,
                sourceContextUrl = updateDto.sourceContextUrl.blankAsNone(),
                sourceMediaUrl = updateDto.sourceMediaUrl.blankAsNone(),
                tagNames = updateDto.tags,
                boardIds = updateDto.boardIds,
                user = user,
            )
            .let { RestResponse.ok(pinResponses.pin(it)) }
    }

    /** A blank address is no address, on the write of one pin as on its creation. */
    private fun String?.blankAsNone(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
