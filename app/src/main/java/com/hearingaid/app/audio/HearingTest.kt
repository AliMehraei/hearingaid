package com.hearingaid.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import com.hearingaid.app.dsp.Bands
import com.hearingaid.app.dsp.Ear
import com.hearingaid.app.model.Audiogram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class TestProgress(val ear: Int, val frequencyHz: Int, val step: Int, val totalSteps: Int)

/**
 * Pure-tone audiometry with the earphones the person will actually use, following a simplified
 * Hughson-Westlake procedure: start clearly audible, go down 10 dB after each "heard", up 5 dB
 * after each miss, and take the level heard on two ascending runs as the threshold.
 *
 * The media volume is pinned to 50 % during the test so results are comparable between runs.
 */
class HearingTest(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Volatile private var lastResponse = 0L

    /** Call when the person taps "I hear it". */
    fun respond() {
        lastResponse = SystemClock.elapsedRealtime()
    }

    suspend fun run(onProgress: (TestProgress) -> Unit): Audiogram {
        val thresholds = Array(2) { FloatArray(Bands.COUNT) { Audiogram.NOT_TESTED } }
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * TEST_VOLUME).roundToInt(), 0)
        val track = createTrack()
        try {
            track.play()
            var step = 0
            for (ear in intArrayOf(Ear.RIGHT, Ear.LEFT)) {
                for (band in TEST_ORDER) {
                    onProgress(TestProgress(ear, Bands.CENTERS_HZ[band], step++, TEST_ORDER.size * 2))
                    thresholds[ear][band] = findThreshold(track, ear, Bands.CENTERS_HZ[band])
                }
            }
        } finally {
            track.release()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
        }
        return Audiogram(thresholds[Ear.LEFT].toList(), thresholds[Ear.RIGHT].toList())
    }

    private suspend fun findThreshold(track: AudioTrack, ear: Int, hz: Int): Float {
        var level = START_DBFS
        // Familiarisation: make sure the tone is heard at all.
        while (!present(track, ear, hz, level)) {
            level += 10f
            if (level > MAX_DBFS) return NO_RESPONSE_DBFS
        }
        val ascendingHits = mutableListOf<Float>()
        var presentations = 0
        level -= 10f
        while (ascendingHits.size < 2 && presentations < MAX_PRESENTATIONS) {
            // Ascend in 5 dB steps until heard.
            while (level <= MAX_DBFS && !present(track, ear, hz, level)) {
                level += 5f
                presentations++
            }
            if (level > MAX_DBFS) return NO_RESPONSE_DBFS
            ascendingHits += level
            level = (level - 10f).coerceAtLeast(MIN_DBFS)
            presentations++
        }
        return ascendingHits.average().toFloat()
    }

    /** Plays one tone after a random pause (so rhythm can't be guessed) and reports whether it was heard. */
    private suspend fun present(track: AudioTrack, ear: Int, hz: Int, dbfs: Float): Boolean {
        delay(Random.nextLong(700, 1800))
        val toneStart = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) { track.write(tone(ear, hz, dbfs), 0, TONE_FRAMES * 2, AudioTrack.WRITE_BLOCKING) }
        delay(RESPONSE_WINDOW_MS)
        return lastResponse >= toneStart
    }

    private fun tone(ear: Int, hz: Int, dbfs: Float): FloatArray {
        val amplitude = 10f.pow(dbfs / 20f)
        val ramp = SAMPLE_RATE * RAMP_MS / 1000
        val out = FloatArray(TONE_FRAMES * 2)
        for (i in 0 until TONE_FRAMES) {
            val edge = minOf(i, TONE_FRAMES - 1 - i)
            val envelope = if (edge < ramp) (0.5 - 0.5 * cos(PI * edge / ramp)).toFloat() else 1f
            out[2 * i + ear] = amplitude * envelope * sin(2 * PI * hz * i / SAMPLE_RATE).toFloat()
        }
        return out
    }

    private fun createTrack() = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(TONE_FRAMES * 2 * 4)
        .build()

    companion object {
        /** Standard clinical order: 1 kHz first, then up, then down. */
        private val TEST_ORDER = intArrayOf(2, 3, 4, 5, 1, 0)
        private const val SAMPLE_RATE = 48000
        private const val TONE_FRAMES = SAMPLE_RATE // 1 second
        private const val RAMP_MS = 40
        /** write() returns once the tone is queued, so this covers the 1 s tone plus ~1.2 s to react. */
        private const val RESPONSE_WINDOW_MS = 2200L
        private const val TEST_VOLUME = 0.5f
        private const val START_DBFS = -50f
        private const val MIN_DBFS = -100f
        private const val MAX_DBFS = -5f
        private const val MAX_PRESENTATIONS = 14

        /** Recorded when even the loudest tone wasn't heard. */
        const val NO_RESPONSE_DBFS = 0f
    }
}
