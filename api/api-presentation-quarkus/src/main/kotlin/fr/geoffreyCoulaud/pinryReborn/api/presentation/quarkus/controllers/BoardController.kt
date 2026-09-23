package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.BoardInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinIdsInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinSortStrategyInputEnum
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.BoardMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.CursorMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinSortStrategyMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.serialization.Base64Json
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardPinLister
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardRecycleBin
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardUpdater
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinBoardSetter
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON as JSON
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder
import java.net.URI
import java.util.UUID

@Path("/api/v1/boards")
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class BoardController(
    private val boardCreator: BoardCreator,
    private val boardGetter: BoardGetter,
    private val boardUpdater: BoardUpdater,
    private val boardPinLister: BoardPinLister,
    private val boardRecycleBin: BoardRecycleBin,
    private val pinBoardSetter: PinBoardSetter,
    private val securityIdentity: SecurityIdentity,
    private val pinResponses: PinResponses,
) {
    @POST
    @Authenticated
    // SmallRye reads the status off the return type, and a runtime ResponseBuilder carries none, so
    // the 201 is declared with the 409 the name constraint answers (spec 2026-08-14 section 12).
    @APIResponse(responseCode = "201", description = "Board created",
        content = [Content(mediaType = JSON, schema = Schema(implementation = BoardOutputDto::class))])
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BODY)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.BOARD_NAME_TAKEN)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun createBoard(@Valid @NotNull dto: BoardInputDto): RestResponse<BoardOutputDto> {
        val user = securityIdentity.getUser()
        val board = boardCreator.create(author = user, name = dto.name, description = dto.description)
        return ResponseBuilder
            .created<BoardOutputDto>(URI("/api/v1/boards/${board.id}"))
            .entity(board.toDto(pinCount = 0))
            .build()
    }

    @GET
    @Authenticated
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = JSON, schema = Schema(implementation = BoardListOutputDto::class))])
    @APIResponse(responseCode = "404", description = "A board left the list while it was being read",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["BOARD_DOES_NOT_EXIST"])]))])
    fun listBoards(): RestResponse<BoardListOutputDto> {
        val user = securityIdentity.getUser()
        val boards = boardGetter.listActiveBoardsForUser(user).map { board ->
            board.toDto(pinCount = boardGetter.countActivePinsForUserBoard(board.id, user))
        }
        return RestResponse.ok(BoardListOutputDto(boards = boards))
    }

    @GET
    @Authenticated
    @Path("/{boardId}")
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = JSON, schema = Schema(implementation = BoardOutputDto::class))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_NOT_FOUND)
    fun getBoard(boardId: UUID): RestResponse<BoardOutputDto> {
        val user = securityIdentity.getUser()
        val board = boardGetter.getActiveBoardForUser(boardId = boardId, reader = user)
        val count = boardGetter.countActivePinsForUserBoard(boardId, user)
        return RestResponse.ok(board.toDto(pinCount = count))
    }

    @PUT
    @Authenticated
    @Path("/{boardId}")
    @APIResponse(responseCode = "200", description = "Board updated",
        content = [Content(mediaType = JSON, schema = Schema(implementation = BoardOutputDto::class))])
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BODY)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_NOT_FOUND)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.BOARD_NAME_TAKEN)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun updateBoard(boardId: UUID, @Valid @NotNull dto: BoardInputDto): RestResponse<BoardOutputDto> {
        val user = securityIdentity.getUser()
        val board = boardUpdater.update(boardId = boardId, name = dto.name, description = dto.description, user = user)
        val count = boardGetter.countActivePinsForUserBoard(boardId, user)
        return RestResponse.ok(board.toDto(pinCount = count))
    }

    @DELETE
    @Authenticated
    @Path("/{boardId}")
    @APIResponse(responseCode = "204", description = "No Content")
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_NOT_FOUND)
    @APIResponse(responseCode = "409", description = "The board is in the recycle bin",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["BOARD_ALREADY_SOFT_DELETED"])]))])
    fun softDeleteBoard(boardId: UUID): RestResponse<Void> {
        val user = securityIdentity.getUser()
        boardRecycleBin.softDelete(boardId = boardId, user = user)
        return RestResponse.noContent()
    }

    @GET
    @Authenticated
    @Path("/{boardId}/pins")
    // SmallRye stops generating the success response as soon as an operation declares one of its own,
    // so the 200 is written out beside the refusals rather than dropped from the contract.
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = JSON, schema = Schema(implementation = PinListOutputDto::class))])
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.BLANK_QUERY)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_NOT_FOUND)
    fun listBoardPins(
        boardId: UUID,
        @QueryParam("cursor") @Base64Json cursorInput: CursorDto? = null,
        @QueryParam("pageSize") pageSizeInput: Int? = null,
        @QueryParam("sort") sortInput: PinSortStrategyInputEnum? = null,
        @QueryParam("q") query: String? = null,
    ): RestResponse<PinListOutputDto> {
        val user = securityIdentity.getUser()
        val pageSize = pageSizeInput ?: DEFAULT_PAGE_SIZE
        val sort = if (sortInput != null) sortInput.toDomain() else PinSortStrategy.CREATED_AT_ASC
        val cursor = cursorInput?.toDomain()
        return boardPinLister
            .listActivePinsForBoard(
                reader = user,
                boardId = boardId,
                cursor = cursor,
                pageSize = pageSize,
                sort = sort,
                query = query,
            )
            .let { RestResponse.ok(pinResponses.page(it)) }
    }

    @POST
    @Authenticated
    @Path("/{boardId}/pins")
    @Operation(summary = "File several pins under the board, all or nothing")
    @APIResponse(responseCode = "204", description = "Pins filed under the board")
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BATCH_BODY)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_OR_PIN_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_OR_PIN_NOT_FOUND)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.PIN_ALREADY_RECYCLED)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun addPinsToBoard(boardId: UUID, @Valid @NotNull dto: PinIdsInputDto): RestResponse<Void> {
        val user = securityIdentity.getUser()
        pinBoardSetter.addPinsToBoard(boardId = boardId, pinIds = dto.pinIds, user = user)
        return RestResponse.noContent()
    }

    @DELETE
    @Authenticated
    @Path("/{boardId}/pins")
    @Operation(summary = "Take several pins out of the board, all or nothing")
    @APIResponse(responseCode = "204", description = "Pins taken out of the board")
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BATCH_BODY)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_OR_PIN_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_OR_PIN_NOT_FOUND)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.PIN_ALREADY_RECYCLED)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun removePinsFromBoard(boardId: UUID, @Valid @NotNull dto: PinIdsInputDto): RestResponse<Void> {
        val user = securityIdentity.getUser()
        pinBoardSetter.removePinsFromBoard(boardId = boardId, pinIds = dto.pinIds, user = user)
        return RestResponse.noContent()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
