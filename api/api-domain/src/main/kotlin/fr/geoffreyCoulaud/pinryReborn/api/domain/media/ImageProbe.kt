package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile

data class ProbeResult(val format: MediaFormat, val width: Int, val height: Int, val frames: Int) {
    val animated: Boolean get() = frames > 1
}

interface ImageProbe {
    /**
     * Validate + measure the staged file. Reject over [maxPixels]. Throws on unsupported/undecodable.
     */
    fun probe(staged: StagedFile, maxPixels: Long): ProbeResult
}
