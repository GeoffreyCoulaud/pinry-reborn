package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl

interface MediaFetcher {
    /** The 2xx body at [sourceUrl], redirects followed through allowed addresses alone; else a [FetchException]. */
    fun openStream(sourceUrl: HttpUrl): FetchedMedia
}
