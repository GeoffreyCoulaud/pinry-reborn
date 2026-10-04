package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import java.time.Duration

@ConfigMapping(prefix = "media", namingStrategy = ConfigMapping.NamingStrategy.SNAKE_CASE)
interface MediaConfig {
    @WithDefault("/var/lib/pinry/media")
    fun dataDir(): String

    @WithDefault("31457280") // 30 MiB
    fun maxImageBytes(): Long

    @WithDefault("52428800") // 50 MiB
    fun maxVideoBytes(): Long

    @WithDefault("120")
    fun maxVideoSeconds(): Long

    @WithDefault("50000000") // 50 megapixels
    fun maxPixels(): Long

    /** How long one decoder run may take before it is destroyed. */
    @WithDefault("PT60S")
    fun decoderTimeout(): Duration

    /** The address space one decoder run may reserve, in bytes, measured in ADR 0050's specification. */
    @WithDefault("2147483648") // 2 GiB
    fun decoderMemory(): Long
}
