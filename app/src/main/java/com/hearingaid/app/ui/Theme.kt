package com.hearingaid.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.hearingaid.app.model.Skin
import com.hearingaid.app.model.TextSize
import com.hearingaid.app.model.ThemeMode

/** Audiogram convention: right ear red circles, left ear blue crosses. */
val RightEarColor = Color(0xFFD32F2F)
val LeftEarColor = Color(0xFF1E63D6)

/** Each skin's main colour for light and dark mode; containers are tinted from it. */
private fun skinColors(skin: Skin): Pair<Color, Color> = when (skin) {
    Skin.TEAL, Skin.WALLPAPER -> Color(0xFF00696E) to Color(0xFF4FD8DE)
    Skin.OCEAN -> Color(0xFF1F5FBF) to Color(0xFF9EC2FF)
    Skin.SUNSET -> Color(0xFFB33F00) to Color(0xFFFFB68F)
    Skin.FOREST -> Color(0xFF2E6B30) to Color(0xFF92D78C)
    Skin.BERRY -> Color(0xFF8E3A8A) to Color(0xFFF6ADEC)
    Skin.HIGH_CONTRAST -> Color(0xFF0033AA) to Color(0xFFFFD600)
}

/** The colour shown on each skin's swatch in the Appearance screen. */
fun skinSwatch(skin: Skin, dark: Boolean): Color = skinColors(skin).let { if (dark) it.second else it.first }

private fun schemeFor(skin: Skin, dark: Boolean): ColorScheme {
    val primary = skinSwatch(skin, dark)
    if (skin == Skin.HIGH_CONTRAST) {
        return if (dark) {
            darkColorScheme(
                primary = primary, onPrimary = Color.Black, secondary = primary, onSecondary = Color.Black,
                primaryContainer = primary, onPrimaryContainer = Color.Black,
                secondaryContainer = primary, onSecondaryContainer = Color.Black,
                background = Color.Black, onBackground = Color.White, surface = Color.Black, onSurface = Color.White,
                surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color.White, outline = Color.White,
                surfaceContainer = Color(0xFF121212),
            )
        } else {
            lightColorScheme(
                primary = primary, onPrimary = Color.White, secondary = primary, onSecondary = Color.White,
                primaryContainer = primary, onPrimaryContainer = Color.White,
                secondaryContainer = primary, onSecondaryContainer = Color.White,
                background = Color.White, onBackground = Color.Black, surface = Color.White, onSurface = Color.Black,
                surfaceVariant = Color(0xFFF0F0F0), onSurfaceVariant = Color.Black, outline = Color.Black,
                surfaceContainer = Color(0xFFF5F5F5),
            )
        }
    }
    return if (dark) {
        val container = lerp(primary, Color.Black, 0.62f)
        darkColorScheme(
            primary = primary, secondary = primary, tertiary = primary,
            primaryContainer = container, secondaryContainer = container,
            onPrimaryContainer = lerp(primary, Color.White, 0.6f), onSecondaryContainer = lerp(primary, Color.White, 0.6f),
        )
    } else {
        val container = lerp(primary, Color.White, 0.84f)
        lightColorScheme(
            primary = primary, secondary = primary, tertiary = primary,
            primaryContainer = container, secondaryContainer = container,
            onPrimaryContainer = lerp(primary, Color.Black, 0.55f), onSecondaryContainer = lerp(primary, Color.Black, 0.55f),
        )
    }
}

@Composable
fun HearingAidTheme(
    mode: ThemeMode,
    skin: Skin,
    textSize: TextSize,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (skin == Skin.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        schemeFor(skin, dark)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textSize.scale)) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
