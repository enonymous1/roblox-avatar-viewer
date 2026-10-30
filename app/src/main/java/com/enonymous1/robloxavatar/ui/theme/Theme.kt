package com.enonymous1.robloxavatar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Palette carried over from the original app's Material 3 color resources
// (seed #247BA0), so the modernized app keeps the original look.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006688),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC2E8FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF705D00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE16E),
    onSecondaryContainer = Color(0xFF221B00),
    tertiary = Color(0xFF006D3B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF96F7B4),
    onTertiaryContainer = Color(0xFF00210E),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FDFF),
    onBackground = Color(0xFF001F25),
    surface = Color(0xFFF8FDFF),
    onSurface = Color(0xFF001F25),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787D),
    inverseOnSurface = Color(0xFFD6F6FF),
    inverseSurface = Color(0xFF00363F),
    inversePrimary = Color(0xFF77D1FF),
    surfaceTint = Color(0xFF006688),
    outlineVariant = Color(0xFFC0C7CD),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF77D1FF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFC2E8FF),
    secondary = Color(0xFFE3C54A),
    onSecondary = Color(0xFF3A3000),
    secondaryContainer = Color(0xFF544600),
    onSecondaryContainer = Color(0xFFFFE16E),
    tertiary = Color(0xFF7ADA99),
    onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF00522B),
    onTertiaryContainer = Color(0xFF96F7B4),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF001F25),
    onBackground = Color(0xFFA6EEFF),
    surface = Color(0xFF001F25),
    onSurface = Color(0xFFA6EEFF),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C7CD),
    outline = Color(0xFF8A9297),
    inverseOnSurface = Color(0xFF001F25),
    inverseSurface = Color(0xFFA6EEFF),
    inversePrimary = Color(0xFF006688),
    surfaceTint = Color(0xFF77D1FF),
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
)

// No custom Typography here on purpose: with the Compose BOM in use
// (Material 3 1.4), Typography's constructor is internal, so a custom
// default font family can no longer be constructed. The app uses
// MaterialTheme's default typography instead. That is also why the
// original Marhey font file was dropped in 2.0.3 rather than shipped
// unused.

@Composable
fun RobloxAvatarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
