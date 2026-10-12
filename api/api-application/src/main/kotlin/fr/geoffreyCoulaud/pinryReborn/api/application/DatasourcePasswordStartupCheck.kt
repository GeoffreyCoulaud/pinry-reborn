package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.DatasourcePasswordCheck
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.interceptor.Interceptor

/**
 * Runs [DatasourcePasswordCheck] before every other startup observer, several of which reach the database: the refusal
 * then precedes the first connection. Here because the order is Quarkus's and the reading is the persistence's.
 */
@ApplicationScoped
class DatasourcePasswordStartupCheck {
    fun onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) ignored: StartupEvent) =
        DatasourcePasswordCheck.verifyConfigured()
}
