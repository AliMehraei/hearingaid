package com.hearingaid.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.hearingaid.app.dsp.DspConfig
import com.hearingaid.app.dsp.HearingProcessor
import com.hearingaid.app.model.HearingSettings
import com.hearingaid.app.model.MicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

enum class EngineError { MIC_PERMISSION, NO_EARPHONES, AUDIO_FAILED }

/** Device types are AudioDeviceInfo.TYPE_* values (or [Headphones.NONE]); the UI turns them into text. */
data class EngineState(
    val running: Boolean = false,
    val error: EngineError? = null,
    val errorDetail: String = "",
    val outputType: Int = Headphones.NONE,
    val outputName: String = "",
    val inputType: Int = Headphones.NONE,
    val inputName: String = "",
    val wireless: Boolean = false,
    /** True while output is routed to the phone speaker; audio is muted to prevent feedback. */
    val mutedNoHeadphones: Boolean = false,
    /** Phone-side latency only; Bluetooth adds its own on top. */
    val latencyMs: Int = 0,
    val inputLevelDb: Float = SILENCE_DB,
    val outputLevelDb: Float = SILENCE_DB,
) {
    companion object {
        const val SILENCE_DB = -90f
    }
}

/**
 * Microphone -> [HearingProcessor] -> earphones, on one high-priority thread using the
 * device's native sample rate and burst size so Android can give us its low-latency "fast" paths.
 */
class AudioEngine(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile private var settings = HearingSettings()
    @Volatile private var processor: HearingProcessor? = null
    @Volatile private var running = false
    @Volatile private var restartRequested = false
    private var thread: Thread? = null
    private var onFatalError: () -> Unit = {}

    fun applySettings(new: HearingSettings) {
        val old = settings
        settings = new
        processor?.config = DspConfig.from(new)
        if (old.micSource != new.micSource || old.voiceProcessing != new.voiceProcessing) restartRequested = true
    }

    @Synchronized
    fun start(onFatalError: () -> Unit): Boolean {
        if (running) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = EngineState(error = EngineError.MIC_PERMISSION)
            return false
        }
        if (Headphones.find(audioManager) == null) {
            _state.value = EngineState(error = EngineError.NO_EARPHONES)
            return false
        }
        this.onFatalError = onFatalError
        running = true
        _state.value = EngineState(running = true)
        thread = Thread(::runLoop, "hearing-audio").also { it.start() }
        return true
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        _state.update { it.copy(running = false, inputLevelDb = EngineState.SILENCE_DB, outputLevelDb = EngineState.SILENCE_DB) }
    }

    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            while (running) {
                restartRequested = false
                runSession()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio failed", e)
            running = false
            _state.value = EngineState(error = EngineError.AUDIO_FAILED, errorDetail = e.message.orEmpty())
            onFatalError()
        }
    }

    /** One record/play session; returns when stopped or when a setting needs new audio objects. */
    @Suppress("MissingPermission") // checked in start()
    private fun runSession() {
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val burst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val s = settings

        val inFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minIn = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val recorder = AudioRecord.Builder()
            .setAudioSource(
                // VOICE_RECOGNITION: flat response and no automatic gain control, unlike the default source.
                if (s.voiceProcessing) MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else MediaRecorder.AudioSource.VOICE_RECOGNITION,
            )
            .setAudioFormat(inFormat)
            .setBufferSizeInBytes(max(minIn, burst * 4 * 2))
            .build()
        preferredMic(s.micSource)?.let { recorder.setPreferredDevice(it) }

        val outFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minOut = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(outFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(minOut)
            .build()
        Headphones.find(audioManager)?.let { track.setPreferredDevice(it) }

        val proc = HearingProcessor(sampleRate, DspConfig.from(s))
        processor = proc
        val input = FloatArray(burst)
        val output = FloatArray(burst * 2)
        // Start with the smallest buffer that holds two bursts; grow only if the device underruns.
        track.bufferSizeInFrames = burst * 2
        var underruns = 0
        var nextCheck = 0L
        var inPeak = 0f
        var outPeak = 0f
        var muted = false

        try {
            recorder.startRecording()
            track.play()
            while (running && !restartRequested) {
                val n = recorder.read(input, 0, burst, AudioRecord.READ_BLOCKING)
                if (n <= 0) throw IllegalStateException("Microphone read failed ($n)")
                proc.process(input, n, output)
                for (i in 0 until n) inPeak = max(inPeak, abs(input[i]))
                if (muted) output.fill(0f, 0, n * 2)
                else for (i in 0 until n * 2) outPeak = max(outPeak, abs(output[i]))
                track.write(output, 0, n * 2, AudioTrack.WRITE_BLOCKING)

                val now = SystemClock.elapsedRealtime()
                if (now >= nextCheck) {
                    nextCheck = now + STATUS_INTERVAL_MS
                    if (track.underrunCount > underruns) {
                        underruns = track.underrunCount
                        track.bufferSizeInFrames = track.bufferSizeInFrames + burst
                    }
                    val out = track.routedDevice
                    val mic = recorder.routedDevice
                    // If earphones are unplugged mid-session, Android reroutes to the loudspeaker.
                    muted = out != null && !Headphones.isHeadphones(out)
                    val latencyFrames = burst + proc.latencyFrames + track.bufferSizeInFrames
                    _state.value = EngineState(
                        running = true,
                        outputType = out?.type ?: Headphones.NONE,
                        outputName = out?.productName?.toString().orEmpty(),
                        inputType = mic?.type ?: Headphones.NONE,
                        inputName = mic?.productName?.toString().orEmpty(),
                        wireless = out != null && Headphones.isWireless(out.type),
                        mutedNoHeadphones = muted,
                        latencyMs = latencyFrames * 1000 / sampleRate,
                        inputLevelDb = toDb(inPeak),
                        outputLevelDb = toDb(outPeak),
                    )
                    inPeak = 0f
                    outPeak = 0f
                }
            }
        } finally {
            processor = null
            runCatching { recorder.stop() }
            runCatching { track.stop() }
            recorder.release()
            track.release()
        }
    }

    private fun preferredMic(source: MicSource): AudioDeviceInfo? {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return when (source) {
            MicSource.AUTO -> null
            MicSource.PHONE -> inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            MicSource.HEADSET -> inputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
        }
    }

    private fun toDb(peak: Float) = if (peak <= 0f) EngineState.SILENCE_DB
    else max(EngineState.SILENCE_DB, 20f * log10(peak))

    private companion object {
        const val TAG = "AudioEngine"
        const val STATUS_INTERVAL_MS = 100L
    }
}
