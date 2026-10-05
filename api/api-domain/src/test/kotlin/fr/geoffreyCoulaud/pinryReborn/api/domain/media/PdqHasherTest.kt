package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

/** The fixtures under `pdq/` and their hashes are written by `pdq/generate.py`, through Meta's C++ reference. */
class PdqHasherTest {
    // The luminance `pdqhash` hands the C++: weighted in double precision, then rounded to a float.
    private fun lumaAsPdqhashComputesIt(name: String): LumaFrame {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/pdq/$name")).use { it.readBytes() }
        val (magic, size, maxValue) = String(bytes, Charsets.ISO_8859_1).split('\n', limit = 4)
        val (width, height) = size.split(' ').map(String::toInt)
        val offset = magic.length + size.length + maxValue.length + 3
        val luma = FloatArray(width * height) { pixel ->
            val (red, green, blue) = (0 until 3).map { bytes[offset + pixel * 3 + it].toUByte().toDouble() }
            (red * 0.299 + green * 0.587 + blue * 0.114).toFloat()
        }
        return LumaFrame(width, height, luma)
    }

    private fun words(hexadecimal: String) = hexadecimal.chunked(16).map { java.lang.Long.parseUnsignedLong(it, 16) }

    @ParameterizedTest
    @CsvFileSource(resources = ["/pdq/hashes.csv"])
    fun `Given a random image's luminance, Then its hash and quality are exactly the C++ reference's`(
        name: String,
        hexadecimal: String,
        quality: Int,
    ) {
        assertEquals(PdqHash(words(hexadecimal), quality), PdqHasher.hash(lumaAsPdqhashComputesIt(name)))
    }

    @Test
    fun `Given a uniform frame, Then its quality is one PDQ discards`() {
        val uniform = LumaFrame(WIDTH, HEIGHT, FloatArray(WIDTH * HEIGHT) { GREY })
        assertTrue(PdqHasher.hash(uniform).quality <= PdqHasher.DISCARDED_QUALITY)
    }

    @Test
    fun `Given a frame under five pixels on a side, Then its hash is all zeros at quality zero`() {
        val narrow = LumaFrame(width = 4, height = HEIGHT, FloatArray(4 * HEIGHT) { it.toFloat() })
        assertEquals(PdqHash(List(4) { 0L }, quality = 0), PdqHasher.hash(narrow))
    }

    @Test
    fun `Given two hashes, Then their distance is the count of bits they differ by`() {
        val zeros = PdqHash(List(4) { 0L }, quality = 100)
        val ones = PdqHash(listOf(1L, 3L, 0L, -1L), quality = 100)
        assertEquals(1 + 2 + 64, zeros.distanceTo(ones))
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 150
        const val GREY = 128f
    }
}
