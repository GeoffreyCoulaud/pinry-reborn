package fr.geoffreyCoulaud.pinryReborn.api.application

import io.quarkus.test.junit.QuarkusTestProfile

/**
 * The deployment's own `api.cors.origins`, taken from the production file: every other case runs on the test resources'
 * `https://app.test`.
 */
class ProductionCorsTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(ORIGINS_KEY to (ProductionProperties[ORIGINS_KEY] ?: error("$ORIGINS_KEY is not declared")))

    companion object {
        const val ORIGINS_KEY = "api.cors.origins"
    }
}
