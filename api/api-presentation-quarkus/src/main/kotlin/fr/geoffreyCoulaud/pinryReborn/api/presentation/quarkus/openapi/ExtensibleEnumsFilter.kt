package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi

import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataExportReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportIssueKindDto
import io.quarkus.smallrye.openapi.OpenApiFilter
import org.eclipse.microprofile.openapi.OASFilter
import org.eclipse.microprofile.openapi.models.OpenAPI

/**
 * An open code lists its known values without closing the set: `oasdiff` reports no value added to an
 * `x-extensible-enum`, where it breaks on an `enum` (`docs/adr/0044-a-response-code-declares-its-set.md`).
 */
@OpenApiFilter(stages = [OpenApiFilter.RunStage.BUILD])
class ExtensibleEnumsFilter : OASFilter {
    override fun filterOpenAPI(openAPI: OpenAPI) {
        EXTENSIBLE.forEach { name ->
            val schema = openAPI.components.schemas.getValue(name)
            schema.addExtension(EXTENSION, schema.enumeration)
            schema.enumeration = null
        }
    }

    companion object {
        const val EXTENSION = "x-extensible-enum"
        val EXTENSIBLE = listOf(
            DownloadReasonDto::class,
            UserDataImportIssueKindDto::class,
            UserDataExportReasonDto::class,
        ).map { it.java.simpleName }
    }
}
