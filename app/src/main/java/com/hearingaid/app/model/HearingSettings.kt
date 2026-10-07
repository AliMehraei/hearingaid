package com.hearingaid.app.model

import androidx.annotation.StringRes
import com.hearingaid.app.R
import com.hearingaid.app.dsp.Bands

/** Listening situations. Offsets are added on top of the person's own prescription. */
enum class Preset(
    @StringRes val titleRes: Int,
    val bandOffsetsDb: FloatArray,
    val compression: Float,
    /** Noise reduction strength when it is switched on (1 = normal). */
    val noiseReduction: Float,
) {
    EVERYDAY(R.string.preset_everyday, floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f), 1f, 1f),
    CONVERSATION(R.string.preset_conversation, floatArrayOf(-4f, -1f, 2f, 4f, 3f, 0f), 1f, 1f),
    NOISY(R.string.preset_noisy, floatArrayOf(-10f, -6f, 0f, 3f, 2f, -2f), 1.4f, 1.6f),
    TV_MUSIC(R.string.preset_tv, floatArrayOf(2f, 1f, 0f, 0f, 0f, 0f), 0.6f, 0.5f),
}

enum class MicSource(@StringRes val titleRes: Int) {
    AUTO(R.string.mic_auto),
    PHONE(R.string.mic_phone),
    HEADSET(R.string.mic_headset),
}

/**
 * Hearing thresholds per test frequency ([Bands.CENTERS_HZ]) in dBFS at the test volume.
 * Lower = better hearing. [NOT_TESTED] marks frequencies that were skipped.
 */
data class Audiogram(val left: List<Float>, val right: List<Float>) {
    companion object {
        val NOT_TESTED = Float.NaN
    }
}

data class HearingSettings(
    val gainsLeft: List<Float> = DEFAULT_GAINS,
    val gainsRight: List<Float> = DEFAULT_GAINS,
    val volumeDb: Float = 15f,
    /** -1 = left only louder, +1 = right only louder. */
    val balance: Float = 0f,
    /** Multiplier on compression ratios; 0 = linear amplification. */
    val compression: Float = 1f,
    val noiseReduction: Boolean = true,
    /** Output limiter ceiling in dBFS. */
    val maxOutputDb: Float = -6f,
    val preset: Preset = Preset.EVERYDAY,
    val micSource: MicSource = MicSource.AUTO,
    /** Use the phone's own call-style noise suppression (VOICE_COMMUNICATION source). */
    val voiceProcessing: Boolean = false,
    val audiogram: Audiogram? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val skin: Skin = Skin.TEAL,
    val textSize: TextSize = TextSize.NORMAL,
    /** Turn the hearing aid on when the app is opened and earphones are connected. */
    val autoStart: Boolean = false,
    /** GitHub build: look for a newer release in the background. */
    val autoCheckUpdates: Boolean = true,
) {
    companion object {
        /** Mild high-frequency tilt: the most common age-related loss pattern, used until a test is taken. */
        val DEFAULT_GAINS = listOf(0f, 3f, 6f, 10f, 12f, 12f)

        init {
            check(DEFAULT_GAINS.size == Bands.COUNT)
        }
    }
}
