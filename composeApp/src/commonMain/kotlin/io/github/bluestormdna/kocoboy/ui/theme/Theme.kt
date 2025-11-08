package io.github.bluestormdna.kocoboy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import io.github.bluestormdna.kocoboy.ui.dmg.ClassicColorTheme
import io.github.bluestormdna.kocoboy.ui.dmg.ColorTheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

val LocalColorTheme = compositionLocalOf<ColorTheme> { ClassicColorTheme }

@Composable
fun KocoBoyTheme(
    colorTheme: ColorTheme,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalColorTheme provides colorTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

object KocoBoyTheme {
    val colors: ColorTheme
        @Composable @ReadOnlyComposable
        get() = LocalColorTheme.current
}
