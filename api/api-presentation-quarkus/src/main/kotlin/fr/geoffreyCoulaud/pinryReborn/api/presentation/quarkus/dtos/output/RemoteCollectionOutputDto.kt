package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output

import org.eclipse.microprofile.openapi.annotations.media.Schema

data class RemoteCollectionOutputDto(val name: String, @field:Schema(format = "uri") val url: String)
