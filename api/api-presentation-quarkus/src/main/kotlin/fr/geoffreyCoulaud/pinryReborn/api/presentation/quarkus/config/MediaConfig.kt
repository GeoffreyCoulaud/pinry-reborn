package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault

@ConfigMapping(prefix = "media", namingStrategy = ConfigMapping.NamingStrategy.SNAKE_CASE)
interface MediaConfig {
    @WithDefault("/var/lib/pinry/media")
    fun dataDir(): String

    @WithDefault("31457280") // 30 MiB
    fun maxImageBytes(): Long

    @WithDefault("52428800") // 50 MiB
    fun maxVideoBytes(): Long

    @WithDefault("50000000") // 50 megapixels
    fun maxPixels(): Long
}
