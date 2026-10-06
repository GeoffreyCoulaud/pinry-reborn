package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FrameSampler
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageTransformer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.LumaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaLimits.Companion.MAX_FRAME_SIDE
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionSpec
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.PpmReader
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessOutcome
import fr.geoffreyCoulaud.pinryReborn.api.utilities.ProcessRunner
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * [ImageTransformer] to WebP at [quality] and an image's [FrameSampler], running `vips` destroyed past [timeout] and
 * capped at [maxAddressSpace] bytes (ADR 0050). Not `@ApplicationScoped`: ARC cannot resolve its plain parameters.
 */
class VipsImageTransformer(private val quality: Int, timeout: Duration, maxAddressSpace: Long) :
    ImageTransformer, FrameSampler {
    private val runner = ProcessRunner(timeout, maxAddressSpace)

    // ponytail: a process per sampled page, 120 at most, each decoding the pages before it; one pass if that matters.
    override fun sample(media: Media, staged: StagedFile, onFrame: (LumaFrame) -> Unit) {
        val pages = if (media.animated) FrameSampler.pages(delays(staged)).map { "[page=$it]" } else listOf("")
        val frame = Files.createTempFile(Path.of(staged.path).toAbsolutePath().parent, "frame-", ".ppm")
        try {
            for (page in pages) {
                run(listOf("vips", "thumbnail", staged.path + page, "$frame[background=255]", "$MAX_FRAME_SIDE"))
                val raster = PpmReader.read(frame, MAX_FRAME_SIDE)
                onFrame(LumaFrame.ofRgb(raster.width, raster.height, raster.rgb))
            }
        } finally {
            Files.deleteIfExists(frame)
        }
    }

    private fun delays(staged: StagedFile): List<Duration> =
        delaysOf(run(listOf("vipsheader", "-f", "delay", staged.path)))

    /** A refusal rather than a parse error, so the fingerprint drain stamps the media rather than failing on it. */
    internal fun delaysOf(header: String): List<Duration> =
        header.trim().split(' ').map { delay ->
            val millis = delay.toLongOrNull() ?: throw UndecodableImageException("vipsheader read a delay of $delay")
            Duration.ofMillis(millis)
        }

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

    private fun run(command: List<String>): String =
        when (val outcome = runner.run(command)) {
            is ProcessOutcome.TimedOut ->
                throw UndecodableImageException("${command.first()} ran past ${outcome.timeout} and was destroyed")
            is ProcessOutcome.Exited -> {
                if (outcome.code != 0) {
                    throw UndecodableImageException("${command.first()} refused it: ${outcome.errors.trim()}")
                }
                outcome.output
            }
        }
}
