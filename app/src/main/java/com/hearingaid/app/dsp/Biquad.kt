package com.hearingaid.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Second-order IIR section (RBJ audio-EQ cookbook), transposed direct form II. */
class Biquad private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.70710677

        fun lowPass(sampleRate: Int, cutoffHz: Float, q: Double = BUTTERWORTH_Q): Biquad {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = ((1 - cosW0) / 2 / a0).toFloat(),
                b1 = ((1 - cosW0) / a0).toFloat(),
                b2 = ((1 - cosW0) / 2 / a0).toFloat(),
                a1 = (-2 * cosW0 / a0).toFloat(),
                a2 = ((1 - alpha) / a0).toFloat(),
            )
        }

        fun highPass(sampleRate: Int, cutoffHz: Float, q: Double = BUTTERWORTH_Q): Biquad {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = ((1 + cosW0) / 2 / a0).toFloat(),
                b1 = (-(1 + cosW0) / a0).toFloat(),
                b2 = ((1 + cosW0) / 2 / a0).toFloat(),
                a1 = (-2 * cosW0 / a0).toFloat(),
                a2 = ((1 - alpha) / a0).toFloat(),
            )
        }
    }
}
