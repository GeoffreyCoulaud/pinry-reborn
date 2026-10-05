package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class FrameSamplerTest {
    private fun millis(vararg values: Long) = values.map(Duration::ofMillis)

    private fun delays(count: Int, millis: Long) = List(count) { Duration.ofMillis(millis) }

    @Test
    fun `Given ten seconds, Then the instants are its ten whole seconds`() {
        assertEquals((0L..9L).map(Duration::ofSeconds), FrameSampler.instants(Duration.ofSeconds(10)))
    }

    @Test
    fun `Given two seconds, Then its two whole seconds are kept among four evenly spaced instants`() {
        assertEquals(millis(0, 500, 1000, 1500), FrameSampler.instants(Duration.ofSeconds(2)))
    }

    @Test
    fun `Given one and a half seconds, Then its two whole seconds are completed to four by the first quarters`() {
        assertEquals(millis(0, 375, 750, 1000), FrameSampler.instants(Duration.ofMillis(1500)))
    }

    @Test
    fun `Given no length, Then the one instant is the start`() {
        assertEquals(millis(0), FrameSampler.instants(Duration.ZERO))
    }

    @Test
    fun `Given eight pages of a second each, Then every page is sampled`() {
        assertEquals((0..7).toList(), FrameSampler.pages(delays(8, 1000)))
    }

    @Test
    fun `Given forty pages of a tenth of a second each, Then the pages at its four seconds are sampled`() {
        assertEquals(listOf(0, 10, 20, 30), FrameSampler.pages(delays(40, 100)))
    }

    @Test
    fun `Given three pages of half a second each, Then each page is sampled once`() {
        assertEquals(listOf(0, 1, 2), FrameSampler.pages(delays(3, 500)))
    }

    @Test
    fun `Given ten pages whose delays are all zero, Then four pages spread over them are sampled`() {
        assertEquals(listOf(0, 2, 5, 7), FrameSampler.pages(delays(10, 0)))
    }

    @Test
    fun `Given three pages whose delays are all zero, Then each page is sampled once`() {
        assertEquals(listOf(0, 1, 2), FrameSampler.pages(delays(3, 0)))
    }

    @Test
    fun `Given a first page shown for no time, Then the page shown at the start is the second`() {
        assertEquals(listOf(1), FrameSampler.pages(millis(0, 100)))
    }

    @Test
    fun `Given red, green, blue and white pixels, Then each luminance is PDQ's weighting of its samples`() {
        val rgb = intArrayOf(255, 0, 0, 0, 255, 0, 0, 0, 255, 255, 255, 255)
        val frame = LumaFrame.ofRgb(width = 2, height = 2, rgb)
        val (red, green, blue) = listOf(0.299f * 255, 0.587f * 255, 0.114f * 255)
        assertArrayEquals(floatArrayOf(red, green, blue, red + green + blue), frame.luma)
        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
    }
}
