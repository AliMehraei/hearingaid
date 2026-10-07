package com.hearingaid.app.dsp

import com.hearingaid.app.model.Audiogram
import com.hearingaid.app.model.Prescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionTest {
    @Test
    fun `flat hearing gets no frequency shaping`() {
        val flat = List(Bands.COUNT) { -70f }
        val fit = Prescription.fit(Audiogram(flat, flat))
        fit.gainsLeft.forEach { assertEquals(0f, it, 0.01f) }
    }

    @Test
    fun `sloping high-frequency loss gets more high-frequency gain`() {
        val sloping = listOf(-75f, -75f, -70f, -55f, -45f, -40f)
        val fit = Prescription.fit(Audiogram(sloping, sloping))
        assertTrue(fit.gainsRight.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(17.5f, fit.gainsRight[5], 0.01f) // half of the 35 dB relative loss
    }

    @Test
    fun `worse ear gets more gain`() {
        val good = List(Bands.COUNT) { -70f }
        val bad = List(Bands.COUNT) { -50f }
        val fit = Prescription.fit(Audiogram(left = bad, right = good))
        assertTrue(fit.gainsLeft[3] - fit.gainsRight[3] >= 9.9f)
    }

    @Test
    fun `untested frequencies are ignored`() {
        val partial = listOf(Float.NaN, -70f, -70f, -60f, Float.NaN, Float.NaN)
        val fit = Prescription.fit(Audiogram(partial, partial))
        assertEquals(5f, fit.gainsLeft[3], 0.01f)
        assertEquals(0f, fit.gainsLeft[4], 0.01f)
    }
}
