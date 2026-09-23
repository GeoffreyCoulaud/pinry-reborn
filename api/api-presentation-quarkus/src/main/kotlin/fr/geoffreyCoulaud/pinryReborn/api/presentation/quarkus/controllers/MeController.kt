package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PasswordChangeInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.ProblemDetail
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE as PROBLEM_JSON
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.UserDtoMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.ReauthenticationHeader
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.security.getUser
import fr.geoffreyCoulaud.pinryReborn.api.usecases.AccountDeleter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PasswordChanger
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.jboss.resteasy.reactive.RestResponse

@Path("/api/v1/me")
class MeController(
    private val securityIdentity: SecurityIdentity,
    private val passwordChanger: PasswordChanger,
    private val accountDeleter: AccountDeleter,
) {
    @GET
    @Authenticated
    fun getCurrentUser(): UserOutputDto = securityIdentity.getUser().toDto()

    @PUT
    @Path("/password")
    @Authenticated
    @APIResponse(responseCode = "204", description = "Password changed, and every session of the account revoked")
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.INVALID_BODY)
    @APIResponse(responseCode = "403", description = "The current password is wrong",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["REAUTHENTICATION_FAILED"])]))])
    @APIResponse(responseCode = "409", description = "Another change of this password landed first",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["PASSWORD_CHANGE_COLLISION"])]))])
    @APIResponse(responseCode = "415", ref = SharedRefusalsFilter.UNSUPPORTED_MEDIA_TYPE)
    @APIResponse(responseCode = "422", description = "The account has held this password before",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code", enumeration = ["PASSWORD_PREVIOUSLY_USED"])]))])
    @APIResponse(responseCode = "429", description = "The attempt limiter, or a change too soon after the last",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(allOf = [ProblemDetail::class],
            properties = [SchemaProperty(name = "code",
                enumeration = ["TOO_MANY_AUTHENTICATION_ATTEMPTS", "PASSWORD_CHANGED_TOO_SOON"])]))])
    fun changePassword(@Valid @NotNull dto: PasswordChangeInputDto): RestResponse<Void> {
        passwordChanger.changePassword(securityIdentity.getUser(), dto.currentPassword, dto.newPassword)
        return RestResponse.noContent()
    }

    @DELETE
    @Authenticated
    @APIResponse(responseCode = "202", description = "Account deletion accepted")
    @APIResponse(responseCode = "400", ref = SharedRefusalsFilter.UNSUPPORTED_REAUTHENTICATION_FACTOR)
    @APIResponse(responseCode = "403", ref = SharedRefusalsFilter.REAUTHENTICATION_HEADER_FAILED)
    @APIResponse(responseCode = "429", ref = SharedRefusalsFilter.TOO_MANY_AUTHENTICATION_ATTEMPTS)
    fun deleteAccount(@HeaderParam(ReauthenticationHeader.HEADER) reauthHeader: String?): RestResponse<Void> {
        val factor = ReauthenticationHeader.parsePasswordFactor(reauthHeader)
        accountDeleter.requestDeletion(securityIdentity.getUser(), factor)
        return RestResponse.ResponseBuilder.create<Void>(RestResponse.Status.ACCEPTED).build()
    }
}
