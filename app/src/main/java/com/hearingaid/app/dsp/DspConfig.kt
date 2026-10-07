package com.hearingaid.app.dsp

import com.hearingaid.app.model.HearingSettings
import kotlin.math.max
import kotlin.math.pow

/**
 * Everything the audio thread needs, precomputed and immutable so the UI can swap it in
 * without locks while audio is running.
 */
class DspConfig(
    /** Static gain per [ear][band] in dB, with volume, balance and preset already folded in. */
    val bandGainDb: Array<FloatArray>,
    /** Compression ratio per [ear][band], applied above [kneeDb]. */
    val ratio: Array<FloatArray>,
    /** Band level (dBFS) above which compression starts: soft sounds get full gain, loud ones less. */
    val kneeDb: Float,
    /** How aggressively estimated steady noise is subtracted; 0 disables noise reduction. */
    val noiseOverSubtraction: Float,
    /** Lowest linear gain noise reduction may apply, so speech in noise never vanishes entirely. */
    val noiseFloorGain: Float,
    /** Linear peak ceiling of the output limiter (maximum power output). */
    val ceiling: Float,
) {
    companion object {
        const val KNEE_DBFS = -60f
        private const val BALANCE_RANGE_DB = 12f
        private const val GAIN_PER_RATIO_STEP_DB = 30f
        private const val MAX_RATIO = 4f

        fun from(s: HearingSettings): DspConfig {
            val preset = s.preset
            val gains = Array(2) { FloatArray(Bands.COUNT) }
            val ratios = Array(2) { FloatArray(Bands.COUNT) }
            for (ear in 0..1) {
                val prescribed = if (ear == Ear.LEFT) s.gainsLeft else s.gainsRight
                val balanceTrim = if (ear == Ear.LEFT) -max(0f, s.balance) * BALANCE_RANGE_DB
                else -max(0f, -s.balance) * BALANCE_RANGE_DB
                for (b in 0 until Bands.COUNT) {
                    val bandGain = prescribed[b] + preset.bandOffsetsDb[b]
                    // More prescribed gain means a narrower comfortable range in the ear, so compress harder.
                    ratios[ear][b] = (1f + s.compression * preset.compression * max(0f, bandGain) / GAIN_PER_RATIO_STEP_DB)
                        .coerceIn(1f, MAX_RATIO)
                    gains[ear][b] = bandGain + s.volumeDb + balanceTrim
                }
            }
            val nr = if (s.noiseReduction) preset.noiseReduction else 0f
            return DspConfig(
                bandGainDb = gains,
                ratio = ratios,
                kneeDb = KNEE_DBFS,
                noiseOverSubtraction = 1.5f * nr,
                noiseFloorGain = 10f.pow(-(4f + 8f * nr) / 20f),
                ceiling = 10f.pow(s.maxOutputDb / 20f),
            )
        }
    }
}
