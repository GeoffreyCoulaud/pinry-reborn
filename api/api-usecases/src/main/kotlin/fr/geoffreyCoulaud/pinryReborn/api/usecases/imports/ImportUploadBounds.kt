package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

/** What a client cuts its chunks at and refuses a file past, which the handshake publishes. */
data class ImportUploadBounds(
    val maxChunkBytes: Long,
    val maxArchiveBytes: Long,
)
