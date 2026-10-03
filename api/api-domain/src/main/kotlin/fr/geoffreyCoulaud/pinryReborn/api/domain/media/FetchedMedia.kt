package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import java.io.InputStream

/** A fetched body and the `Content-Type` its response declared, if any; closing it ends the download. */
class FetchedMedia(val stream: InputStream, val contentType: String?) : AutoCloseable by stream
