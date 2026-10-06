package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PageMediaExtractor
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StorageLayout
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.AddressPolicy
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.GuardingProxy
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.HttpMediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp.YtDlpPageMediaExtractor
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaDownloadConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.nio.file.Path
import java.time.Duration

/**
 * CDI wiring for [MediaFetcher] and [PageMediaExtractor] in the composition root: only this module may depend on the
 * fetch adapters. Each download's proxy takes the SSRF address policy from config: the Standard guard by default, or
 * AllowAll when `media.download.allow_private_addresses=true` (trusted networks / tests).
 */
@ApplicationScoped
class FetchAdapterProducers {
    @Produces
    @ApplicationScoped
    fun mediaFetcher(config: MediaDownloadConfig): MediaFetcher =
        HttpMediaFetcher(
            connectTimeout = config.connectTimeout(),
            requestTimeout = config.requestTimeout(),
            maxRedirects = config.maxRedirects(),
            bodyTimeout = config.extractionTimeout(),
        ) {
            proxy(config)
        }

    @Produces
    @ApplicationScoped
    fun pageMediaExtractor(config: MediaDownloadConfig, media: MediaConfig): PageMediaExtractor =
        YtDlpPageMediaExtractor(
            stagingDirectory = Path.of(media.dataDir()).resolve(StorageLayout.STAGING_DIRECTORY),
            maxBytes = media.maxVideoBytes(),
            maxDuration = Duration.ofSeconds(media.maxVideoSeconds()),
            timeout = config.extractionTimeout(),
        ) {
            proxy(config)
        }

    private fun proxy(config: MediaDownloadConfig): GuardingProxy {
        val policy = if (config.allowPrivateAddresses()) AddressPolicy.AllowAll else AddressPolicy.Standard
        return GuardingProxy(policy, config.connectTimeout())
    }
}
