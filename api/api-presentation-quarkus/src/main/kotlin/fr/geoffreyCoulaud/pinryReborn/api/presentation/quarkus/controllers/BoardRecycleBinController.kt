package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.BoardIdsInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.BoardOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.RecycledBoardListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.BoardMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.BoardMapper.toRecycledDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardRecycleBin
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.DELETE
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

@Path("/api/v1/boards/recycled")
class BoardRecycleBinController(
    private val boardRecycleBin: BoardRecycleBin,
    private val boardGetter: BoardGetter,
    private val securityIdentity: SecurityIdentity,
) {
    @GET
    @Authenticated
    fun listRecycledBoards(): RestResponse<RecycledBoardListOutputDto> {
        val user = securityIdentity.getUser()
        val boards = boardRecycleBin.listRecycledBoardsForUser(user).map { it.toRecycledDto() }
        return RestResponse.ok(RecycledBoardListOutputDto(boards))
    }

    @POST
    @Authenticated
    @Path("/{boardId}/restore")
    @APIResponse(responseCode = "200", description = "OK",
        content = [Content(mediaType = JSON, schema = Schema(implementation = BoardOutputDto::class))])
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_NOT_FOUND)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.BOARD_NOT_RECYCLED)
    fun restoreBoard(boardId: UUID): RestResponse<BoardOutputDto> {
        val user = securityIdentity.getUser()
        val board = boardRecycleBin.restore(boardId = boardId, user = user)
        val count = boardGetter.countActivePinsForUserBoard(board.id, user)
        return RestResponse.ok(board.toDto(pinCount = count))
    }

    @POST
    @Authenticated
    @Path("/restore")
    @Operation(summary = "Restore several boards, all or nothing")
    @APIResponse(responseCode = "204", description = "Boards restored")
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BATCH_BODY)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", description = "A board the body names does not exist",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["BOARD_DOES_NOT_EXIST"])]))])
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.BOARD_NOT_RECYCLED)
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun restoreBoards(@Valid @NotNull dto: BoardIdsInputDto): RestResponse<Void> {
        val user = securityIdentity.getUser()
        boardRecycleBin.restoreAll(boardIds = dto.boardIds, user = user)
        return RestResponse.noContent()
    }

    @DELETE
    @Authenticated
    @Path("/{boardId}")
    @APIResponse(responseCode = "204", description = "No Content")
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.BOARD_FORBIDDEN)
    @APIResponse(responseCode = "404", ref = SharedRefusalsFilter.BOARD_NOT_FOUND)
    @APIResponse(responseCode = "409", ref = SharedRefusalsFilter.BOARD_NOT_RECYCLED)
    fun permanentlyDeleteBoard(boardId: UUID): RestResponse<Void> {
        val user = securityIdentity.getUser()
        boardRecycleBin.permanentlyDelete(boardId = boardId, user = user)
        return RestResponse.noContent()
    }

    @DELETE
    @Authenticated
    fun emptyRecycleBin(): RestResponse<Void> {
        val user = securityIdentity.getUser()
        boardRecycleBin.emptyRecycleBin(user = user)
        return RestResponse.noContent()
    }
}
