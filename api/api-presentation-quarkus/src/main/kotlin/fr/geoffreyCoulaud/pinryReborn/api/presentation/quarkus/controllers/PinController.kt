package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinBoardsInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinCreationInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinSortStrategyInputEnum
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinTagsInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinSortStrategyMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.serialization.Base64Json
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinBoardSetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinRecycleBin
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinTagger
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
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class PinController(
    private val pinCreator: PinCreator,
    private val pinGetter: PinGetter,
    private val pinTagger: PinTagger,
    private val pinRecycleBin: PinRecycleBin,
    private val pinBoardSetter: PinBoardSetter,
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
            sourceContextUrl = creationDto.sourceContextUrl?.takeIf { it.isNotBlank() },
            sourceMediaUrl = creationDto.sourceMediaUrl?.takeIf { it.isNotBlank() },
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
    @Path("/{pinId}/tags")
    @Operation(
        summary = "Replace the pin's tags",
        description = "A tag name is an identity per author under an ASCII fold: `Landscape` and `landscape` " +
            "are one tag, and the response carries the stored spelling, not the one sent. The fold covers " +
            "A to Z only, so `ÉTÉ` and `été` stay two tags.",
    )
    fun setTags(pinId: UUID, @Valid tagsDto: PinTagsInputDto): RestResponse<PinOutputDto> {
        val user = securityIdentity.getUser()
        return pinTagger
            .setTags(pinId = pinId, tagNames = tagsDto.tags, user = user)
            .let { RestResponse.ok(pinResponses.pin(it)) }
    }

    @PUT
    @Authenticated
    @Path("/{pinId}/boards")
    fun setBoards(pinId: UUID, @Valid boardsDto: PinBoardsInputDto): RestResponse<PinOutputDto> {
        val user = securityIdentity.getUser()
        return pinBoardSetter
            .setBoards(pinId = pinId, boardIds = boardsDto.boardIds, user = user)
            .let { RestResponse.ok(pinResponses.pin(it)) }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
