package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import io.smallrye.config.ConfigMapping
import java.util.Optional

@ConfigMapping(
    prefix = "api",
    namingStrategy = ConfigMapping.NamingStrategy.SNAKE_CASE,
)
interface ApiConfig {
    fun host(): String

    fun port(): Int

    fun cors(): Cors

    interface Cors {
        /**
         * Allowed CORS origins, interpolated into `quarkus.http.cors.origins`, which is what the filter reads: this
         * member keeps `api.*` validated. Optional because SmallRye reads the empty list as null.
         */
        fun origins(): Optional<String>
    }
}
