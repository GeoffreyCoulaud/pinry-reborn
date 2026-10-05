package fr.geoffreyCoulaud.pinryReborn.api.utilities

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class PpmReaderTest {
    @TempDir
    lateinit var directory: Path

    private fun file(header: String, vararg raster: Int): Path =
        Files.write(directory.resolve("frame.ppm"), header.toByteArray() + raster.map(Int::toByte).toByteArray())

    @Test
    fun `Given a PPM with a comment as vips writes it, Then its samples are read row by row`() {
        // Given
        val path = file("P6\n#vips2ppm - a date\n2 1\n255\n", 1, 2, 3, 250, 251, 252)
        // When
        val raster = PpmReader.read(path, maxSide = 2)
        // Then
        assertEquals(2, raster.width)
        assertEquals(1, raster.height)
        assertArrayEquals(intArrayOf(1, 2, 3, 250, 251, 252), raster.rgb)
    }

    @Test
    fun `Given a 16-bit PGM, Then each grey is scaled to a byte and repeated over three samples`() {
        // Given: a header on one line, its tokens apart by more than one space
        val path = file("P5  1 2 65535\n", 0xFF, 0xFF, 0x80, 0x00)
        // When / Then
        assertArrayEquals(intArrayOf(255, 255, 255, 128, 128, 128), PpmReader.read(path, maxSide = 2).rgb)
    }

    @Test
    fun `Given a PPM wider than the bound, Then it is refused before its pixels are read`() {
        val path = file("P6\n3 1\n255\n")
        val error = assertThrows(IOException::class.java) { PpmReader.read(path, maxSide = 2) }
        assertEquals("3x1 is past 2 pixels a side", error.message)
    }

    @Test
    fun `Given a PPM taller than the bound, Then it is refused`() {
        assertThrows(IOException::class.java) { PpmReader.read(file("P6\n1 3\n255\n"), maxSide = 2) }
    }

    @Test
    fun `Given a raster shorter than its header, Then it is refused`() {
        assertThrows(IOException::class.java) { PpmReader.read(file("P6\n1 1\n255\n", 1, 2), maxSide = 2) }
    }

    @Test
    fun `Given a PNG's signature, Then it is refused as no binary PPM`() {
        assertThrows(IOException::class.java) { PpmReader.read(file("\u0089PNG\r\n"), maxSide = 2) }
    }

    @Test
    fun `Given a header ending inside a comment, Then it is refused`() {
        assertThrows(IOException::class.java) { PpmReader.read(file("P6\n# no end"), maxSide = 2) }
    }
}
