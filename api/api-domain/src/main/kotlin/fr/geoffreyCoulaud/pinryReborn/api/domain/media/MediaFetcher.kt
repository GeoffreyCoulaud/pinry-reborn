package fr.geoffreyCoulaud.pinryReborn.api.domain.media

interface MediaFetcher {
    /**
     * Reach only the addresses the deployment allows, follow redirects (capped), require a 2xx
     * response, and return its body with its `Content-Type`. Throws a typed [FetchException] on any
     * failure. Does not read/validate image content (that is [ImageProbe]'s job). The caller owns
     * closing the returned media.
     */
    fun openStream(sourceUrl: String): FetchedMedia
}
