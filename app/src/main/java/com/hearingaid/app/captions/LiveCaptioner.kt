package com.hearingaid.app.captions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hearingaid.app.R

/** A message for the UI to localize; [code] fills the generic-failure message. */
data class CaptionError(@StringRes val message: Int, val code: Int = 0)

/**
 * Continuous speech-to-text for people who can't be helped by amplification alone.
 * Android's recognizer handles one utterance per session, so we restart it after every result.
 */
class LiveCaptioner(private val context: Context) : RecognitionListener {
    val lines = mutableStateListOf<String>()
    var partial by mutableStateOf("")
        private set
    var listening by mutableStateOf(false)
        private set
    var error by mutableStateOf<CaptionError?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var consecutiveFailures = 0
    private var languageTag = ""

    fun start(languageTag: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = CaptionError(R.string.cap_err_unavailable)
            return
        }
        error = null
        consecutiveFailures = 0
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
        this.languageTag = languageTag
        listening = true
        listen()
    }

    fun stop() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        partial = ""
    }

    fun clear() {
        lines.clear()
        partial = ""
    }

    private fun listen() {
        if (!listening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        recognizer?.startListening(intent)
    }

    private fun restart(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            recognizer?.cancel()
            listen()
        }, delayMs)
    }

    override fun onResults(results: Bundle?) {
        consecutiveFailures = 0
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add(it) }
        partial = ""
        restart(RESTART_DELAY_MS)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.let { partial = it }
    }

    override fun onError(code: Int) {
        when (code) {
            // Silence or unclear speech: perfectly normal, just keep listening.
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(RESTART_DELAY_MS)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                error = CaptionError(R.string.cap_err_permission)
                stop()
            }
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                error = CaptionError(R.string.cap_err_language)
                stop()
            }
            else -> {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_FAILURES) {
                    error = when (code) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            CaptionError(R.string.cap_err_network)
                        else -> CaptionError(R.string.cap_err_generic, code)
                    }
                    stop()
                } else {
                    restart(RETRY_DELAY_MS)
                }
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private companion object {
        const val RESTART_DELAY_MS = 100L
        const val RETRY_DELAY_MS = 800L
        const val MAX_FAILURES = 5

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE (API 31)
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
