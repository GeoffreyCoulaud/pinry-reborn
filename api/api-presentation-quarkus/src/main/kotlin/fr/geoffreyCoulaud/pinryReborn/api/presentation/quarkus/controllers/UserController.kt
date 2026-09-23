package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.UserInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.UserDtoMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.UserCreator
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON as JSON
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse

@Path("/api/v1/users")
class UserController(
    private val userCreator: UserCreator,
) {
    @POST
    @PermitAll
    @APIResponse(responseCode = "200", description = "The account created",
        content = [Content(mediaType = JSON, schema = Schema(implementation = UserOutputDto::class))])
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BODY)
    @APIResponse(responseCode = "409", description = "Another account holds this name, whatever its case",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["USERNAME_ALREADY_EXISTS"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    fun createUser(@Valid @NotNull userDto: UserInputDto): RestResponse<UserOutputDto> {
        val userOutputDto = userCreator.createUserWithPassword(name = userDto.name, password = userDto.password).toDto()
        return RestResponse.ok(userOutputDto)
    }
}
