package ru.openflux.deployer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    secondary = AccentSuccess,
    error = AccentDanger
)

@Composable
fun OpenFluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
