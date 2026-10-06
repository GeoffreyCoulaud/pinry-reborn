package fr.geoffreyCoulaud.pinryReborn.api.domain.media

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The adapters' tests hold the rest of the sampling, through real media. */
class FrameSamplerTest {
    private fun millis(vararg values: Long) = values.map(Duration::ofMillis)

    @Test
    fun `Given ten seconds, Then the instants are its ten whole seconds`() {
        assertEquals((0L..9L).map(Duration::ofSeconds), FrameSampler.instants(Duration.ofSeconds(10)))
    }

    @Test
    fun `Given an hour, Then the instants are its first hundred and twenty seconds`() {
        assertEquals((0L..119L).map(Duration::ofSeconds), FrameSampler.instants(Duration.ofHours(1)))
    }

    @Test
    fun `Given one and a half seconds, Then its two whole seconds are completed to four by the first quarters`() {
        assertEquals(millis(0, 375, 750, 1000), FrameSampler.instants(Duration.ofMillis(1500)))
    }

    @Test
    fun `Given ten pages whose delays are all zero, Then four pages spread over them are sampled`() {
        assertEquals(listOf(0, 2, 5, 7), FrameSampler.pages(List(10) { Duration.ZERO }))
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
