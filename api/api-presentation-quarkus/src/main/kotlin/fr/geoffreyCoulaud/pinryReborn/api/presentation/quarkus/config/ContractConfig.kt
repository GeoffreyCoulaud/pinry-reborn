package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import io.smallrye.config.ConfigMapping

/**
 * The version SmallRye stamps on the published document, which is what the handshake serves: one declaration for the
 * document and the route, so the two cannot drift.
 */
@ConfigMapping(prefix = "quarkus.smallrye-openapi")
interface ContractConfig {
    fun infoVersion(): String
}
