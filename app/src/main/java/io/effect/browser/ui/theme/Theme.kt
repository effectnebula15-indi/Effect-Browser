package io.effect.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECBFF),
    surface = Color(0xFF14161A),
    background = Color(0xFF0E1013),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B63C4),
    surface = Color(0xFFF7F8FA),
    background = Color(0xFFFFFFFF),
)

/** Palette of container colours offered in the editor. Kept short so the picker stays one row. */
val ContainerPalette: List<Color> = listOf(
    Color(0xFF4C8DF6),
    Color(0xFF37B679),
    Color(0xFFE0A020),
    Color(0xFFD4573F),
    Color(0xFF9B59D0),
    Color(0xFF16A3A3),
    Color(0xFFDB5A8C),
    Color(0xFF7D8894),
)

@Composable
fun EffectBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // System bar colouring is handled by enableEdgeToEdge() in BrowserActivity; setting
    // window.statusBarColor directly is deprecated and ignored on recent Android.
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
