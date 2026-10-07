package com.hearingaid.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class HearingProcessorTest {
    private val sampleRate = 48000

    private fun config(
        gainDb: Float = 0f,
        ratio: Float = 1f,
        noiseOverSubtraction: Float = 0f,
        ceiling: Float = 1f,
        left: FloatArray? = null,
    ) = DspConfig(
        bandGainDb = arrayOf(left ?: FloatArray(Bands.COUNT) { gainDb }, FloatArray(Bands.COUNT) { gainDb }),
        ratio = Array(2) { FloatArray(Bands.COUNT) { ratio } },
        kneeDb = DspConfig.KNEE_DBFS,
        noiseOverSubtraction = noiseOverSubtraction,
        noiseFloorGain = 10f.pow(-12f / 20f),
        ceiling = ceiling,
    )

    private fun sine(hz: Int, dbfs: Float, seconds: Double = 1.0): FloatArray {
        val amp = 10f.pow(dbfs / 20f)
        return FloatArray((sampleRate * seconds).toInt()) { amp * sin(2 * PI * hz * it / sampleRate).toFloat() }
    }

    private fun noise(dbfs: Float, seconds: Double): FloatArray {
        val amp = 10f.pow(dbfs / 20f) * 1.732f // uniform [-a, a] has RMS a/sqrt(3)
        val rnd = Random(42)
        return FloatArray((sampleRate * seconds).toInt()) { amp * (rnd.nextFloat() * 2 - 1) }
    }

    /** Mean-square level in dB of one channel over the last [fraction] of the output. */
    private fun levelDb(stereo: FloatArray, channel: Int, fraction: Double = 0.5): Float {
        val frames = stereo.size / 2
        val from = (frames * (1 - fraction)).toInt()
        var sum = 0.0
        for (i in from until frames) sum += stereo[2 * i + channel].toDouble().pow(2)
        return (10 * log10(sum / (frames - from))).toFloat()
    }

    private fun run(cfg: DspConfig, input: FloatArray): FloatArray {
        val out = FloatArray(input.size * 2)
        val processor = HearingProcessor(sampleRate, cfg)
        // Feed in device-sized bursts, as the audio engine does.
        val burst = 192
        val outChunk = FloatArray(burst * 2)
        var pos = 0
        while (pos < input.size) {
            val count = minOf(burst, input.size - pos)
            processor.process(input.copyOfRange(pos, pos + count), count, outChunk)
            System.arraycopy(outChunk, 0, out, pos * 2, count * 2)
            pos += count
        }
        return out
    }

    @Test
    fun `flat settings pass sound through unchanged at every band`() {
        for (hz in Bands.CENTERS_HZ) {
            val out = run(config(), sine(hz, -20f))
            assertEquals("$hz Hz", -23f, levelDb(out, Ear.LEFT), 0.5f)
            assertEquals("$hz Hz", -23f, levelDb(out, Ear.RIGHT), 0.5f)
        }
    }

    @Test
    fun `band gain boosts its own pitch and leaves others alone`() {
        val left = FloatArray(Bands.COUNT).also { it[4] = 20f } // +20 dB at 4 kHz, left ear only
        val cfg = config(left = left)
        fun boost(hz: Int) = run(cfg, sine(hz, -40f)).let { levelDb(it, Ear.LEFT) - levelDb(it, Ear.RIGHT) }
        assertEquals("4 kHz", 20f, boost(4000), 1f)
        assertTrue("1 kHz ${boost(1000)}", abs(boost(1000)) < 1.5f)
        assertTrue("250 Hz ${boost(250)}", abs(boost(250)) < 0.5f)
    }

    @Test
    fun `compression gives loud sounds less gain than soft ones`() {
        val cfg = config(gainDb = 20f, ratio = 3f)
        val softGain = levelDb(run(cfg, sine(1000, -70f)), 0) - (-73f)
        val loudGain = levelDb(run(cfg, sine(1000, -20f)), 0) - (-23f)
        assertEquals("soft sounds get the full gain", 20f, softGain, 1f)
        assertTrue("soft $softGain dB vs loud $loudGain dB", softGain - loudGain > 15f)
    }

    @Test
    fun `limiter never lets output exceed the ceiling`() {
        val ceiling = 10f.pow(-6f / 20f)
        val out = run(config(gainDb = 40f, ceiling = ceiling), sine(2000, -3f))
        val peak = out.fold(0f) { m, v -> max(m, abs(v)) }
        assertTrue("peak $peak", peak <= ceiling + 1e-6f)
    }

    @Test
    fun `noise reduction lowers steady noise`() {
        val input = noise(-50f, 4.0)
        val off = levelDb(run(config(), input), 0, fraction = 0.25)
        val on = levelDb(run(config(noiseOverSubtraction = 1.5f), input), 0, fraction = 0.25)
        assertTrue("off $off dB, on $on dB", off - on > 6f)
    }

    @Test
    fun `noise reduction keeps speech-like bursts well above the noise`() {
        val bursts = sine(1000, -20f, 4.0)
        val period = sampleRate * 6 / 10
        for (i in bursts.indices) if (i % period >= period / 2) bursts[i] = 0f // 300 ms on, 300 ms off
        val noisy = noise(-60f, 4.0).also { n -> for (i in n.indices) n[i] += bursts[i] }
        val off = levelDb(run(config(), noisy), 0, fraction = 0.5)
        val on = levelDb(run(config(noiseOverSubtraction = 1.5f), noisy), 0, fraction = 0.5)
        assertTrue("speech lost ${off - on} dB", off - on < 1.5f)
    }
}
