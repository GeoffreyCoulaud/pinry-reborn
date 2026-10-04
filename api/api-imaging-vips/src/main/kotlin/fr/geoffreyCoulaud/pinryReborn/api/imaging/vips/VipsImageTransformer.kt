package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessOutcome
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessRunner
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * [ImageTransformer] running `vips thumbnail` from the `PATH`, destroyed past [timeout] and capped at
 * [maxAddressSpace] bytes (ADR 0050), its WebP encoded at [quality].
 *
 * Not `@ApplicationScoped`: ARC cannot resolve its plain constructor parameters, so a producer in
 * the composition root builds it (mirrors `FilesystemMediaStore`).
 */
class VipsImageTransformer(private val quality: Int, timeout: Duration, maxAddressSpace: Long) : ImageTransformer {
    private val runner = ProcessRunner(timeout, maxAddressSpace)

    private companion object {
        private val HEX = HexFormat.of()

        // thumbnail's own maximum: the box is unbounded along the longer side.
        const val UNBOUNDED = 100_000_000
    }

    // A render failure must leave no output temp behind; the input temp is always removed.
    @Suppress("TooGenericExceptionCaught")
    override fun render(source: InputStream, spec: RenditionSpec): StagedFile {
        val input = Files.createTempFile("rendition-in-", ".tmp")
        val output = Files.createTempFile("rendition-out-", ".webp")
        try {
            Files.newOutputStream(input).use { source.copyTo(it) }
            run(thumbnail(input, output, spec))
            val bytes = Files.readAllBytes(output)
            val hash = HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            return StagedFile(output.toString(), bytes.size.toLong(), hash)
        } catch (error: Throwable) {
            Files.deleteIfExists(output)
            throw error
        } finally {
            Files.deleteIfExists(input)
        }
    }

    // A box whose shorter side is the frame's: thumbnail fits inside it, never upscales, and keeps the stored
    // orientation. n=-1 loads every frame; a still's loader, lacking the option, is never handed it.
    private fun thumbnail(input: Path, output: Path, spec: RenditionSpec): List<String> {
        val (width, height) =
            if (spec.frameWidth <= spec.frameHeight) spec.shortestSide to UNBOUNDED else UNBOUNDED to spec.shortestSide
        val frames = if (spec.animated) "[n=-1]" else ""
        return listOf("vips", "thumbnail", "$input$frames", "$output[Q=$quality]", "$width") +
            listOf("--height", "$height", "--size", "down", "--no-rotate")
    }

    private fun run(command: List<String>) {
        when (val outcome = runner.run(command)) {
            is ProcessOutcome.TimedOut ->
                throw UndecodableImageException("vips ran past ${outcome.timeout} and was destroyed")
            is ProcessOutcome.Exited ->
                if (outcome.code != 0) throw UndecodableImageException("vips refused it: ${outcome.errors.trim()}")
        }
    }
}
