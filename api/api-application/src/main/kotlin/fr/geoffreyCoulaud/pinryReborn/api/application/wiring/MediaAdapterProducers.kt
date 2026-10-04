package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessor
import fr.geoffreyCoulaud.pinryReborn.api.imaging.vips.VipsImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.imaging.vips.VipsImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.RenditionsConfig
import fr.geoffreyCoulaud.pinryReborn.api.storage.filesystem.FilesystemMediaStore
import fr.geoffreyCoulaud.pinryReborn.api.storage.filesystem.FilesystemRenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.usecases.MediaBounds
import fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg.FfmpegVideoProcessor
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.time.Duration

/**
 * CDI wiring for [MediaStore], [RenditionCache] and [ImageTransformer], hosted in the composition
 * root (`api-application`): this is the only module that may depend on the
 * `api-storage-filesystem` and `api-imaging-vips` infrastructure adapters, so the producers live
 * here rather than in the presentation layer (which must depend on `api-usecases`/`api-domain`
 * only).
 *
 * `FilesystemMediaStore`, `FilesystemRenditionCache` and `VipsImageTransformer` are deliberately
 * not `@ApplicationScoped` (see their kdoc) since ARC cannot resolve their plain constructor
 * parameters on its own. These producers are the single place that construct them, sourcing
 * `dataDir` from [MediaConfig] and `webpQuality` from [RenditionsConfig].
 */
@ApplicationScoped
class MediaAdapterProducers {
    @Produces
    @ApplicationScoped
    fun mediaStore(config: MediaConfig): MediaStore = FilesystemMediaStore(config.dataDir())

    @Produces
    @ApplicationScoped
    fun renditionCache(config: MediaConfig): RenditionCache = FilesystemRenditionCache(config.dataDir())

    @Produces
    @ApplicationScoped
    fun mediaBounds(config: MediaConfig): MediaBounds =
        MediaBounds(
            config.maxImageBytes(), config.maxVideoBytes(), Duration.ofSeconds(config.maxVideoSeconds()),
            config.maxPixels(),
        )

    @Produces
    @ApplicationScoped
    fun videoProcessor(config: MediaConfig, renditions: RenditionsConfig): VideoProcessor =
        FfmpegVideoProcessor(config.decoderTimeout(), config.decoderMemory(), renditions.webpQuality())

    @Produces
    @ApplicationScoped
    fun imageProbe(config: MediaConfig): ImageProbe = VipsImageProbe(config.decoderTimeout(), config.decoderMemory())

    @Produces
    @ApplicationScoped
    fun imageTransformer(config: MediaConfig, renditions: RenditionsConfig): ImageTransformer =
        VipsImageTransformer(renditions.webpQuality(), config.decoderTimeout(), config.decoderMemory())
}
