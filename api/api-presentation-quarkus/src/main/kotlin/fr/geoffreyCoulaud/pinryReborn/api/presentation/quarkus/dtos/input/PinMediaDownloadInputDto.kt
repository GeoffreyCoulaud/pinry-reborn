package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import org.eclipse.microprofile.openapi.annotations.media.Schema

data class PinMediaDownloadInputDto(@field:HttpAddress @field:Schema(format = "uri") val sourceUrl: String)
