package com.hearingaid.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** A language offered in the pickers, named in its own script so people can find it. */
data class Language(val tag: String, val nativeName: String)

/**
 * In-app language choice. Applied by wrapping each Activity/Service base context, so it works on
 * every Android version (not only 13+ per-app languages) and also flips layouts to right-to-left.
 */
object AppLocale {
    private const val PREFS = "hearing"
    private const val KEY = "appLanguage"

    /** Languages with a full UI translation. Order: English, then requested languages, then others. */
    val UI_LANGUAGES = listOf(
        Language("en", "English"),
        Language("fa", "فارسی"),
        Language("ar", "العربية"),
        Language("ckb", "کوردی (سۆرانی)"),
        Language("ku", "Kurdî (Kurmancî)"),
        Language("tr", "Türkçe"),
        Language("zh", "中文 (简体)"),
        Language("de", "Deutsch"),
        Language("ru", "Русский"),
        Language("it", "Italiano"),
        Language("fr", "Français"),
        Language("es", "Español"),
        Language("pt", "Português"),
        Language("hi", "हिन्दी"),
        Language("ur", "اردو"),
        Language("ja", "日本語"),
        Language("ko", "한국어"),
        Language("id", "Bahasa Indonesia"),
        Language("uk", "Українська"),
    )

    /** Speech-recognition locales for captions; recognizers need a region-qualified tag. */
    val SPEECH_LANGUAGES = listOf(
        Language("en-US", "English (US)"),
        Language("en-GB", "English (UK)"),
        Language("fa-IR", "فارسی"),
        Language("ar-SA", "العربية"),
        Language("ar-EG", "العربية (مصر)"),
        Language("ckb-IQ", "کوردی (سۆرانی)"),
        Language("ku-TR", "Kurdî (Kurmancî)"),
        Language("tr-TR", "Türkçe"),
        Language("zh-CN", "中文 (普通话)"),
        Language("zh-TW", "中文 (台灣)"),
        Language("yue-HK", "粵語"),
        Language("de-DE", "Deutsch"),
        Language("ru-RU", "Русский"),
        Language("it-IT", "Italiano"),
        Language("fr-FR", "Français"),
        Language("es-ES", "Español"),
        Language("pt-BR", "Português"),
        Language("hi-IN", "हिन्दी"),
        Language("ur-PK", "اردو"),
        Language("ja-JP", "日本語"),
        Language("ko-KR", "한국어"),
        Language("id-ID", "Bahasa Indonesia"),
        Language("uk-UA", "Українська"),
    )

    /** Empty = follow the phone's language. */
    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun wrap(base: Context): Context {
        val tag = current(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun set(activity: Activity, tag: String) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        HearingAidApp.createChannel(activity)
        activity.recreate()
    }
}
