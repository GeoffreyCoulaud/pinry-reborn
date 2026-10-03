package fr.geoffreyCoulaud.pinryReborn.api.domain.media

interface PageMediaExtractor {
    /**
     * The video the page at [pageUrl] shows, its file deleted once the stream closes, [heartbeat] called while the
     * extraction runs. Else a [PageExtractionException], or a [FetchException] for what the network refused.
     */
    fun extract(pageUrl: String, heartbeat: () -> Unit): FetchedMedia
}

/** Base for what the page itself makes the extractor refuse (ADR 0048, decision 3). */
sealed class PageExtractionException(message: String) : Exception(message)

/** The page shows no video the extractor can download. */
class NoMediaFoundException(message: String) : PageExtractionException(message)
