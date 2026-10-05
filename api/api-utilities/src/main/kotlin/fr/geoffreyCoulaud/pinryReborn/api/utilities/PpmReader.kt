package fr.geoffreyCoulaud.pinryReborn.api.utilities

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/** A raster, three samples a pixel from 0 to 255, row by row. */
class Raster(val width: Int, val height: Int, val rgb: IntArray)

/**
 * Reads the binary PPM or PGM a capped decoder wrote, refusing one past [read]'s bound on either side before reading
 * its pixels: it decodes nothing (ADR 0051, consequences).
 */
object PpmReader {
    private const val MAX_BYTE = 255
    private const val RGB = 3

    fun read(path: Path, maxSide: Int): Raster =
        BufferedInputStream(Files.newInputStream(path)).use { input ->
            val magic = token(input)
            refuseUnless(magic == "P6" || magic == "P5") { "Not a binary PPM or PGM: $magic" }
            val channels = if (magic == "P6") RGB else 1
            val width = number(input)
            val height = number(input)
            val maxValue = number(input)
            refuseUnless(maxOf(width, height) <= maxSide) { "${width}x$height is past $maxSide pixels a side" }
            val bytesPerSample = if (maxValue > MAX_BYTE) 2 else 1
            val length = width * height * channels * bytesPerSample
            val raster = input.readNBytes(length)
            refuseUnless(raster.size == length) { "Truncated raster: ${raster.size} of $length bytes" }
            fun byteAt(index: Int) = raster[index].toUByte().toInt()
            // A 16-bit sample is big-endian, and each is scaled to a byte's range.
            fun sample(index: Int): Int {
                val wide = { byteAt(2 * index) shl Byte.SIZE_BITS or byteAt(2 * index + 1) }
                val value = if (bytesPerSample == 1) byteAt(index) else wide()
                return (value * MAX_BYTE + maxValue / 2) / maxValue
            }
            Raster(width, height, IntArray(width * height * RGB) { sample(if (channels == 1) it / RGB else it) })
        }

    private fun refuseUnless(condition: Boolean, message: () -> String) {
        if (!condition) throw IOException(message())
    }

    private fun number(input: InputStream): Int =
        token(input).let { token -> token.toIntOrNull() ?: throw IOException("Not a number in the header: $token") }

    // Whitespace and comments separate the header's tokens; the one whitespace ending the last starts the raster.
    private fun token(input: InputStream): String {
        var byte = input.read()
        while (byte == '#'.code || Character.isWhitespace(byte)) {
            if (byte == '#'.code) skipLine(input)
            byte = input.read()
        }
        val token = StringBuilder()
        while (byte != -1 && !Character.isWhitespace(byte)) {
            token.append(byte.toChar())
            byte = input.read()
        }
        return token.toString()
    }

    private fun skipLine(input: InputStream) {
        do {
            val byte = input.read()
        } while (byte != '\n'.code && byte != -1)
    }
}
