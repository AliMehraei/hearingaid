package com.hearingaid.app.model

/**
 * Turns an in-app audiogram into band gains.
 *
 * Consumer earphones aren't calibrated, so absolute dB HL can't be known. We therefore use the
 * *shape* of the audiogram: each frequency's loss is measured against the person's best-heard
 * frequency (either ear), and the half-gain rule with a NAL-R style low-frequency cut is applied.
 * Overall loudness is left to the volume slider, with a rough starting point derived from the
 * best threshold.
 */
object Prescription {
    const val MAX_BAND_GAIN_DB = 40f

    /** Less low-pitch gain, as NAL-R does: low frequencies mostly carry noise and mask speech. */
    private val LOW_FREQUENCY_CORRECTION = floatArrayOf(-6f, -2f, 0f, 0f, 0f, 0f)

    /** Rough threshold of normal hearing for typical earbuds at the 50 % test volume. */
    private const val NOMINAL_NORMAL_THRESHOLD_DBFS = -80f

    data class Fit(val gainsLeft: List<Float>, val gainsRight: List<Float>, val volumeDb: Float)

    fun fit(audiogram: Audiogram): Fit {
        val measured = (audiogram.left + audiogram.right).filterNot { it.isNaN() }
        if (measured.isEmpty()) {
            return Fit(HearingSettings.DEFAULT_GAINS, HearingSettings.DEFAULT_GAINS, 15f)
        }
        val best = measured.min()

        fun ear(thresholds: List<Float>) = thresholds.mapIndexed { band, threshold ->
            val relativeLoss = if (threshold.isNaN()) 0f else threshold - best
            (0.5f * relativeLoss + LOW_FREQUENCY_CORRECTION[band]).coerceIn(0f, MAX_BAND_GAIN_DB)
        }

        val volume = (0.5f * (best - NOMINAL_NORMAL_THRESHOLD_DBFS)).coerceIn(5f, 30f)
        return Fit(ear(audiogram.left), ear(audiogram.right), volume)
    }
}
