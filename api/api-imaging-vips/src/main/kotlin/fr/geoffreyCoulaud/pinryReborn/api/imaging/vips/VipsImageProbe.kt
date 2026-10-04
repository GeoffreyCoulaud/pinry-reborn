package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.MediaFormat
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbe
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ProbeResult
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UnsupportedImageFormatException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessOutcome
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessRunner
import java.time.Duration

/**
 * [ImageProbe] running the `vipsheader` on the `PATH`, which reads the header alone and decodes no pixel, destroyed
 * past [timeout] and capped at [maxAddressSpace] bytes (ADR 0050).
 */
class VipsImageProbe(timeout: Duration, maxAddressSpace: Long) : ImageProbe {
    private val runner = ProcessRunner(timeout, maxAddressSpace)

    override fun probe(staged: StagedFile): ProbeResult {
        val fields = header(staged)
        val frames = fields.getOrNull(STILL_FIELDS)?.toInt() ?: 1
        return ProbeResult(formatOf(fields[0]), fields[1].toInt(), fields[2].toInt(), frames, staged.byteSize)
    }

    // Fields libvips sets itself, never `-a`, where a file's own comment can forge a line.
    // vipsheader exits 1 at the first field it lacks, having printed the others: a still has no n-pages.
    private fun header(staged: StagedFile): List<String> =
        when (val outcome = runner.run(HEADER + staged.path)) {
            is ProcessOutcome.TimedOut ->
                throw UndecodableImageException("vipsheader ran past ${outcome.timeout} and was destroyed")
            is ProcessOutcome.Exited -> {
                val fields = outcome.output.trim().lines()
                if (outcome.code != 0 && fields.size != STILL_FIELDS) {
                    throw UndecodableImageException("vipsheader refused ${staged.path}: ${outcome.errors.trim()}")
                }
                fields
            }
        }

    private fun formatOf(loader: String): MediaFormat =
        when (loader) {
            "pngload" -> MediaFormat.PNG
            "jpegload" -> MediaFormat.JPEG
            "webpload" -> MediaFormat.WEBP
            "gifload" -> MediaFormat.GIF
            else -> throw UnsupportedImageFormatException("Unsupported image loader: $loader")
        }

    private companion object {
        val HEADER = listOf("vipsheader", "-f", "vips-loader", "-f", "width", "-f", "height", "-f", "n-pages")
        const val STILL_FIELDS = 3
    }
}
