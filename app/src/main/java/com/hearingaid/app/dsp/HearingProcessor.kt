package com.hearingaid.app.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mono microphone in, stereo (left/right ear) out, using a weighted overlap-add (WOLA) FFT
 * filter bank like the ones in commercial hearing aids:
 *
 *   high-pass 80 Hz -> sqrt-Hann window -> FFT
 *     -> per-band level -> per-ear band gains (prescription + WDRC compression)
 *     -> gains interpolated across FFT bins on a log-frequency scale
 *     -> spectral noise reduction (minimum-statistics noise estimate + Wiener-style gain)
 *     -> inverse FFT (both ears in one transform) -> overlap-add -> per-ear peak limiter.
 *
 * With unity gains the filter bank reconstructs the input exactly. Algorithmic delay is one
 * frame: 256 samples (5.3 ms) at 48 kHz.
 */
class HearingProcessor(sampleRate: Int, initial: DspConfig) {
    @Volatile
    var config: DspConfig = initial

    private val n = frameSize(sampleRate)
    private val hop = n / 4
    private val bins = n / 2 + 1
    private val fft = Fft(n)
    private val window = FloatArray(n) { sqrt(0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }

    /** sqrt-Hann analysis x synthesis = Hann, which sums to n / (2 * hop) at this overlap. */
    private val overlapAddScale = 2f * hop / n

    /** Parseval with the window's energy (n/2), so band power matches time-domain mean square. */
    private val powerNorm = 2f / (n.toFloat() * n)

    private val highPass = Biquad.highPass(sampleRate, 80f)
    private val input = FloatArray(n)
    private var filled = 0
    private val ready = Array(2) { FloatArray(hop) }
    private val overlap = Array(2) { FloatArray(n) }
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val outRe = FloatArray(n)
    private val outIm = FloatArray(n)

    private val bandOfBin = IntArray(bins)
    private val lowerBand = IntArray(bins)
    private val upperWeight = FloatArray(bins)
    private val bandAccumulator = FloatArray(Bands.COUNT)
    private val bandPower = FloatArray(Bands.COUNT) { 1e-10f }
    private val bandGainDb = Array(2) { FloatArray(Bands.COUNT) }
    private val binGain = Array(2) { FloatArray(bins) }

    private val smoothedPower = FloatArray(bins)
    private val runningMin = FloatArray(bins) { Float.MAX_VALUE }
    private val subWindowMins = Array(NOISE_SUBWINDOWS) { FloatArray(bins) { Float.MAX_VALUE } }
    private var subWindow = 0
    private var framesInSubWindow = 0
    private val framesPerSubWindow = max(1, (NOISE_WINDOW_SECONDS / NOISE_SUBWINDOWS * sampleRate / hop).toInt())
    private val noiseGain = FloatArray(bins) { 1f }
    private var firstFrame = true

    private val attack = smoothing(sampleRate, hop, 0.005)
    private val release = smoothing(sampleRate, hop, 0.080)
    private val powerSmoothing = smoothing(sampleRate, hop, 0.020)
    private val noiseGainRise = smoothing(sampleRate, hop, 0.003)
    private val noiseGainFall = smoothing(sampleRate, hop, 0.040)
    private val limiterRelease = (1.0 - exp(-1.0 / (0.050 * sampleRate))).toFloat()
    private val limiterGain = floatArrayOf(1f, 1f)

    init {
        for (k in 0 until bins) {
            val hz = k.toFloat() * sampleRate / n
            bandOfBin[k] = Bands.CROSSOVERS_HZ.indexOfFirst { hz < it }.let { if (it < 0) Bands.COUNT - 1 else it }
            val centers = Bands.CENTERS_HZ
            when {
                hz <= centers.first() -> { lowerBand[k] = 0; upperWeight[k] = 0f }
                hz >= centers.last() -> { lowerBand[k] = Bands.COUNT - 2; upperWeight[k] = 1f }
                else -> {
                    val lo = centers.indexOfLast { it <= hz }
                    lowerBand[k] = lo
                    upperWeight[k] = (ln(hz / centers[lo]) / ln(centers[lo + 1].toFloat() / centers[lo]))
                }
            }
        }
    }

    /** Processing delay in samples. */
    val latencyFrames: Int get() = n

    /** [output] receives interleaved stereo, so it must hold 2 * [frames] samples. */
    fun process(source: FloatArray, frames: Int, output: FloatArray) {
        val cfg = config
        for (i in 0 until frames) {
            input[n - hop + filled] = highPass.process(source[i])
            output[2 * i] = limit(Ear.LEFT, ready[Ear.LEFT][filled], cfg.ceiling)
            output[2 * i + 1] = limit(Ear.RIGHT, ready[Ear.RIGHT][filled], cfg.ceiling)
            if (++filled == hop) {
                processFrame(cfg)
                System.arraycopy(input, hop, input, 0, n - hop)
                filled = 0
            }
        }
    }

    private fun processFrame(cfg: DspConfig) {
        for (t in 0 until n) {
            re[t] = input[t] * window[t]
            im[t] = 0f
        }
        fft.transform(re, im, inverse = false)

        bandAccumulator.fill(0f)
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            bandAccumulator[bandOfBin[k]] += if (k == 0 || k == n / 2) p else 2f * p
        }
        for (b in 0 until Bands.COUNT) {
            val p = bandAccumulator[b] * powerNorm
            bandPower[b] += (if (p > bandPower[b]) attack else release) * (p - bandPower[b])
            val levelDb = 10f * log10(bandPower[b] + 1e-12f)
            val over = max(0f, levelDb - cfg.kneeDb)
            for (ear in 0..1) {
                bandGainDb[ear][b] = cfg.bandGainDb[ear][b] - over * (1f - 1f / cfg.ratio[ear][b])
            }
        }
        for (ear in 0..1) {
            val g = bandGainDb[ear]
            for (k in 0 until bins) {
                val lo = lowerBand[k]
                val db = g[lo] + upperWeight[k] * (g[lo + 1] - g[lo])
                binGain[ear][k] = 10f.pow(db / 20f)
            }
        }
        updateNoiseReduction(cfg)

        // Both ears' spectra are Hermitian (real signals), so one inverse FFT of L + jR yields
        // the left ear in the real part and the right ear in the imaginary part.
        for (k in 0 until n) {
            val m = if (k <= n / 2) k else n - k
            val xr = re[m]
            val xi = if (k <= n / 2) im[m] else -im[m]
            val gl = binGain[Ear.LEFT][m] * noiseGain[m]
            val gr = binGain[Ear.RIGHT][m] * noiseGain[m]
            outRe[k] = xr * gl - xi * gr
            outIm[k] = xi * gl + xr * gr
        }
        fft.transform(outRe, outIm, inverse = true)

        val left = overlap[Ear.LEFT]
        val right = overlap[Ear.RIGHT]
        for (t in 0 until n) {
            val w = window[t] * overlapAddScale
            left[t] += outRe[t] * w
            right[t] += outIm[t] * w
        }
        System.arraycopy(left, 0, ready[Ear.LEFT], 0, hop)
        System.arraycopy(right, 0, ready[Ear.RIGHT], 0, hop)
        for (buf in overlap) {
            System.arraycopy(buf, hop, buf, 0, n - hop)
            buf.fill(0f, n - hop, n)
        }
    }

    /**
     * The noise floor in each bin is the minimum smoothed power over the last ~1.5 s (speech always
     * has pauses, steady noise doesn't), then bins are attenuated by how far they sit above it.
     */
    private fun updateNoiseReduction(cfg: DspConfig) {
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            smoothedPower[k] = if (firstFrame) p else smoothedPower[k] + powerSmoothing * (p - smoothedPower[k])
            runningMin[k] = min(runningMin[k], smoothedPower[k])
        }
        firstFrame = false
        if (++framesInSubWindow >= framesPerSubWindow) {
            framesInSubWindow = 0
            System.arraycopy(runningMin, 0, subWindowMins[subWindow], 0, bins)
            subWindow = (subWindow + 1) % NOISE_SUBWINDOWS
            runningMin.fill(Float.MAX_VALUE)
        }
        if (cfg.noiseOverSubtraction <= 0f) {
            noiseGain.fill(1f)
            return
        }
        for (k in 0 until bins) {
            var noise = runningMin[k]
            for (w in subWindowMins) noise = min(noise, w[k])
            noise *= NOISE_MIN_BIAS
            val ratio = noise / (smoothedPower[k] + 1e-20f)
            val target = max(cfg.noiseFloorGain, sqrt(max(0f, 1f - cfg.noiseOverSubtraction * ratio)))
            val coef = if (target > noiseGain[k]) noiseGainRise else noiseGainFall
            noiseGain[k] += coef * (target - noiseGain[k])
        }
    }

    /** Instant-attack peak limiter: nothing leaves above the ceiling, whatever the gains are. */
    private fun limit(ear: Int, x: Float, ceiling: Float): Float {
        var g = limiterGain[ear]
        g += limiterRelease * (1f - g)
        val peak = abs(x)
        if (peak * g > ceiling) g = ceiling / peak
        limiterGain[ear] = g
        return (x * g).coerceIn(-ceiling, ceiling)
    }

    private companion object {
        const val NOISE_WINDOW_SECONDS = 1.5
        const val NOISE_SUBWINDOWS = 8

        /** The minimum of a fluctuating power sits below its mean; this compensates (~ +3 dB). */
        const val NOISE_MIN_BIAS = 2f

        /** ~5 ms frames: short enough for lip-sync, long enough for useful frequency resolution. */
        fun frameSize(sampleRate: Int): Int {
            var size = 64
            while (size < sampleRate * 0.005) size *= 2
            return size
        }

        fun smoothing(sampleRate: Int, hop: Int, seconds: Double) =
            (1.0 - exp(-hop / (seconds * sampleRate))).toFloat()
    }
}
