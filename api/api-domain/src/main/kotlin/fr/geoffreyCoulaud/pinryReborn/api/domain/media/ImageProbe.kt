package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile

data class ProbeResult(
    val format: MediaFormat,
    override val width: Int,
    override val height: Int,
    override val frames: Int,
    override val bytes: Long,
) : MeasuredMedia {
    val animated: Boolean get() = frames > 1
}

interface ImageProbe {
    /** Measure the staged file, which [MediaLimits] judges. Throws on unsupported/undecodable. */
    fun probe(staged: StagedFile): ProbeResult
}
