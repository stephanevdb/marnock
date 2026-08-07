package com.marnock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = MarnockColors.Teal,
    onPrimary = MarnockColors.OnTeal,
    primaryContainer = MarnockColors.TealWash,
    onPrimaryContainer = MarnockColors.TealDeep,
    secondary = MarnockColors.InkMuted,
    onSecondary = MarnockColors.OnTeal,
    secondaryContainer = MarnockColors.MistDeep,
    onSecondaryContainer = MarnockColors.Ink,
    tertiary = MarnockColors.Relay,
    onTertiary = MarnockColors.OnTeal,
    tertiaryContainer = Color(0xFFD0E8FF),
    onTertiaryContainer = Color(0xFF001E33),
    error = MarnockColors.Danger,
    onError = MarnockColors.OnTeal,
    errorContainer = MarnockColors.DangerContainer,
    onErrorContainer = MarnockColors.OnDangerContainer,
    background = MarnockColors.Mist,
    onBackground = MarnockColors.Ink,
    surface = MarnockColors.Surface,
    onSurface = MarnockColors.Ink,
    surfaceVariant = MarnockColors.MistDeep,
    onSurfaceVariant = MarnockColors.InkMuted,
    surfaceTint = MarnockColors.Teal,
    inverseSurface = MarnockColors.Ink,
    inverseOnSurface = MarnockColors.Mist,
    inversePrimary = MarnockColors.TealBright,
    outline = MarnockColors.Hairline,
    outlineVariant = MarnockColors.MistSlate,
    scrim = Color(0xCC0B1220),
    surfaceBright = MarnockColors.SurfaceElevated,
    surfaceDim = MarnockColors.MistDeep,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFC),
    surfaceContainer = MarnockColors.Mist,
    surfaceContainerHigh = MarnockColors.MistDeep,
    surfaceContainerHighest = MarnockColors.MistSlate
)

private val DarkScheme = darkColorScheme(
    primary = MarnockColors.DarkPrimary,
    onPrimary = MarnockColors.DarkOnPrimary,
    primaryContainer = MarnockColors.DarkPrimaryContainer,
    onPrimaryContainer = MarnockColors.DarkOnPrimaryContainer,
    secondary = MarnockColors.DarkOnSurfaceVariant,
    onSecondary = MarnockColors.DarkSurfaceContainerLowest,
    secondaryContainer = MarnockColors.DarkSurfaceContainerHigh,
    onSecondaryContainer = MarnockColors.DarkOnSurface,
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF00344F),
    tertiaryContainer = Color(0xFF004D6E),
    onTertiaryContainer = Color(0xFFC8E7FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = MarnockColors.DarkSurfaceContainerLowest,
    onBackground = MarnockColors.DarkOnSurface,
    surface = MarnockColors.DarkSurface,
    onSurface = MarnockColors.DarkOnSurface,
    surfaceVariant = MarnockColors.DarkSurfaceContainer,
    onSurfaceVariant = MarnockColors.DarkOnSurfaceVariant,
    surfaceTint = MarnockColors.DarkPrimary,
    inverseSurface = MarnockColors.Mist,
    inverseOnSurface = MarnockColors.Ink,
    inversePrimary = MarnockColors.Teal,
    outline = MarnockColors.DarkOutline,
    outlineVariant = MarnockColors.DarkSurfaceContainerHigh,
    scrim = Color(0xCC000000),
    surfaceBright = MarnockColors.DarkSurfaceContainerHigh,
    surfaceDim = MarnockColors.DarkSurfaceContainerLowest,
    surfaceContainerLowest = MarnockColors.DarkSurfaceContainerLowest,
    surfaceContainerLow = MarnockColors.DarkSurfaceContainerLow,
    surfaceContainer = MarnockColors.DarkSurfaceContainer,
    surfaceContainerHigh = MarnockColors.DarkSurfaceContainerHigh,
    surfaceContainerHighest = MarnockColors.DarkSurfaceContainerHighest
)

@Immutable
data class MarnockExtraColors(
    val connected: Color = MarnockColors.Connected,
    val connecting: Color = MarnockColors.Connecting,
    val offline: Color = MarnockColors.Offline,
    val relay: Color = MarnockColors.Relay
)

val LocalMarnockExtra = staticCompositionLocalOf { MarnockExtraColors() }

object MarnockExtra {
    val colors: MarnockExtraColors
        @Composable get() = LocalMarnockExtra.current
}

@Composable
fun MarnockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extras = if (darkTheme) {
        MarnockExtraColors(
            connected = MarnockColors.DarkPrimary,
            connecting = Color(0xFF38BDF8),
            offline = MarnockColors.DarkOutline,
            relay = Color(0xFF7DD3FC)
        )
    } else {
        MarnockExtraColors()
    }

    CompositionLocalProvider(LocalMarnockExtra provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MarnockTypography,
            shapes = MarnockShapes,
            content = content
        )
    }
}
