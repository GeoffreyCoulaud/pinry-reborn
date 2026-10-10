package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl

interface PageMediaExtractor {
    /**
     * The video the page at [pageUrl] shows, its file deleted once the stream closes, [heartbeat] called while the
     * extraction runs. Else a [PageExtractionException], or a [FetchException] for what the network refused.
     */
    fun extract(pageUrl: HttpUrl, heartbeat: () -> Unit): FetchedMedia
}

/** Base for what the page itself makes the extractor refuse (ADR 0048, decision 3). */
sealed class PageExtractionException(message: String) : Exception(message)

/** The page declares a video longer than the bound. */
class PageMediaTooLongException(message: String) : PageExtractionException(message)

/** The page shows no video the extractor can download, a live stream included. */
class NoMediaFoundException(message: String) : PageExtractionException(message)
