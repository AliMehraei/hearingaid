package com.hearingaid.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.random.Random

class FftTest {
    @Test
    fun `cosine lands in its own bin`() {
        val n = 64
        val re = FloatArray(n) { cos(2 * PI * 5 * it / n).toFloat() }
        val im = FloatArray(n)
        Fft(n).transform(re, im, inverse = false)
        assertEquals(n / 2f, hypot(re[5], im[5]), 1e-3f)
        assertEquals(n / 2f, hypot(re[n - 5], im[n - 5]), 1e-3f)
        assertEquals(0f, hypot(re[6], im[6]), 1e-3f)
    }

    @Test
    fun `inverse undoes forward`() {
        val n = 256
        val rnd = Random(1)
        val original = FloatArray(n) { rnd.nextFloat() - 0.5f }
        val re = original.copyOf()
        val im = FloatArray(n)
        val fft = Fft(n)
        fft.transform(re, im, inverse = false)
        fft.transform(re, im, inverse = true)
        for (i in 0 until n) {
            assertEquals(original[i], re[i], 1e-5f)
            assertEquals(0f, im[i], 1e-5f)
        }
    }
}
