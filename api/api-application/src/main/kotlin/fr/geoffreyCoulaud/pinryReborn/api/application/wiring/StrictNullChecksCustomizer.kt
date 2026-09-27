package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

/** A `null` in a `List<UUID>` body is refused as the Kotlin type says, rather than reaching a non-null parameter. */
@Singleton
class StrictNullChecksCustomizer : ObjectMapperCustomizer {
    override fun customize(mapper: ObjectMapper) {
        mapper.registerModule(KotlinModule.Builder().enable(KotlinFeature.NewStrictNullChecks).build())
    }
}
