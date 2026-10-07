package com.hearingaid.app.model

import androidx.annotation.StringRes
import com.hearingaid.app.R

enum class ThemeMode(@StringRes val titleRes: Int) {
    SYSTEM(R.string.mode_system),
    LIGHT(R.string.mode_light),
    DARK(R.string.mode_dark),
}

/** Colour skins. [WALLPAPER] uses Android 12+ wallpaper colours and falls back to [TEAL] on older phones. */
enum class Skin(@StringRes val titleRes: Int) {
    TEAL(R.string.skin_teal),
    OCEAN(R.string.skin_ocean),
    SUNSET(R.string.skin_sunset),
    FOREST(R.string.skin_forest),
    BERRY(R.string.skin_berry),
    HIGH_CONTRAST(R.string.skin_contrast),
    WALLPAPER(R.string.skin_wallpaper),
}

/** Multiplies the phone's own font size, for people who also have low vision. */
enum class TextSize(@StringRes val titleRes: Int, val scale: Float) {
    NORMAL(R.string.text_normal, 1f),
    LARGE(R.string.text_large, 1.15f),
    EXTRA_LARGE(R.string.text_xl, 1.3f),
}
