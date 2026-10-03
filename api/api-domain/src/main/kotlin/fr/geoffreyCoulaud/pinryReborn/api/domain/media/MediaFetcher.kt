package fr.geoffreyCoulaud.pinryReborn.api.domain.media

interface MediaFetcher {
    /** The 2xx body at [sourceUrl], redirects followed through allowed addresses alone; else a [FetchException]. */
    fun openStream(sourceUrl: String): FetchedMedia
}
