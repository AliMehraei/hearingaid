package com.hearingaid.app.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("hearing", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<HearingSettings> = _settings.asStateFlow()

    var safetyNoticeAccepted: Boolean
        get() = prefs.getBoolean(KEY_SAFETY, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFETY, value).apply()

    /** BCP-47 tag for captions; empty = same as the app's language. */
    var captionLanguage: String
        get() = prefs.getString(KEY_CAPTION_LANGUAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CAPTION_LANGUAGE, value).apply()

    /** When the last background update check ran (epoch ms). */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    /** The release whose banner the person closed; the banner returns for the next one. */
    var dismissedUpdate: String
        get() = prefs.getString(KEY_DISMISSED_UPDATE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DISMISSED_UPDATE, value).apply()

    fun update(transform: (HearingSettings) -> HearingSettings): HearingSettings {
        val next = _settings.updateAndGet(transform)
        prefs.edit().putString(KEY_SETTINGS, toJson(next).toString()).apply()
        return next
    }

    private fun load(): HearingSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return HearingSettings()
        return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(HearingSettings())
    }

    private fun toJson(s: HearingSettings) = JSONObject().apply {
        put("gainsLeft", s.gainsLeft.toJson())
        put("gainsRight", s.gainsRight.toJson())
        put("volumeDb", s.volumeDb.toDouble())
        put("balance", s.balance.toDouble())
        put("compression", s.compression.toDouble())
        put("noiseReduction", s.noiseReduction)
        put("maxOutputDb", s.maxOutputDb.toDouble())
        put("preset", s.preset.name)
        put("micSource", s.micSource.name)
        put("voiceProcessing", s.voiceProcessing)
        put("themeMode", s.themeMode.name)
        put("skin", s.skin.name)
        put("textSize", s.textSize.name)
        put("autoStart", s.autoStart)
        put("autoCheckUpdates", s.autoCheckUpdates)
        s.audiogram?.let {
            put("audiogram", JSONObject().put("left", it.left.toJson()).put("right", it.right.toJson()))
        }
    }

    private fun fromJson(o: JSONObject): HearingSettings {
        val d = HearingSettings()
        return HearingSettings(
            gainsLeft = o.optJSONArray("gainsLeft")?.toFloats() ?: d.gainsLeft,
            gainsRight = o.optJSONArray("gainsRight")?.toFloats() ?: d.gainsRight,
            volumeDb = o.optDouble("volumeDb", d.volumeDb.toDouble()).toFloat(),
            balance = o.optDouble("balance", d.balance.toDouble()).toFloat(),
            compression = o.optDouble("compression", d.compression.toDouble()).toFloat(),
            noiseReduction = o.optBoolean("noiseReduction", d.noiseReduction),
            maxOutputDb = o.optDouble("maxOutputDb", d.maxOutputDb.toDouble()).toFloat(),
            preset = enumOrDefault(o.optString("preset"), d.preset),
            micSource = enumOrDefault(o.optString("micSource"), d.micSource),
            voiceProcessing = o.optBoolean("voiceProcessing", d.voiceProcessing),
            audiogram = o.optJSONObject("audiogram")?.let {
                Audiogram(it.getJSONArray("left").toFloats(), it.getJSONArray("right").toFloats())
            },
            themeMode = enumOrDefault(o.optString("themeMode"), d.themeMode),
            skin = enumOrDefault(o.optString("skin"), d.skin),
            textSize = enumOrDefault(o.optString("textSize"), d.textSize),
            autoStart = o.optBoolean("autoStart", d.autoStart),
            autoCheckUpdates = o.optBoolean("autoCheckUpdates", d.autoCheckUpdates),
        )
    }

    // NaN isn't valid JSON, so untested frequencies are stored as null.
    private fun List<Float>.toJson() = JSONArray().also { a ->
        forEach { a.put(if (it.isNaN()) JSONObject.NULL else it.toDouble()) }
    }

    private fun JSONArray.toFloats() = List(length()) { i ->
        if (isNull(i)) Float.NaN else getDouble(i).toFloat()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_SAFETY = "safetyAccepted"
        const val KEY_CAPTION_LANGUAGE = "captionLanguage"
        const val KEY_LAST_UPDATE_CHECK = "lastUpdateCheck"
        const val KEY_DISMISSED_UPDATE = "dismissedUpdate"
    }
}
