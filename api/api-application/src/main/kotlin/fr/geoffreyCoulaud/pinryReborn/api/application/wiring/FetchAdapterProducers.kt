package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.AddressPolicy
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.GuardingProxy
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.HttpMediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaDownloadConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

/**
 * CDI wiring for [MediaFetcher] in the composition root: only this module may depend on the
 * `api-fetch-http` adapter. Each download's proxy takes the SSRF address policy from config: the Standard
 * guard by default, or AllowAll when `media.download.allow_private_addresses=true` (trusted networks / tests).
 */
@ApplicationScoped
class FetchAdapterProducers {
    @Produces
    @ApplicationScoped
    fun mediaFetcher(config: MediaDownloadConfig): MediaFetcher {
        val policy = if (config.allowPrivateAddresses()) AddressPolicy.AllowAll else AddressPolicy.Standard
        return HttpMediaFetcher(config.connectTimeout(), config.requestTimeout(), config.maxRedirects()) {
            GuardingProxy(policy, config.connectTimeout())
        }
    }
}
