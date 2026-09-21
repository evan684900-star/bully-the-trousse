package com.bullythetrousse.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème Material adossé à la palette du site (voir Palette.kt) : le site
 * s'ouvre en thème sombre, donc `darkColorScheme`. Les écrans portés
 * dessinent surtout leurs couleurs à la main (comme le CSS), ce thème ne
 * sert qu'aux composants Material qui restent (boutons, etc.).
 */
private val BullyColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = ButtonSecondary,
    onSecondary = Color.White,
    tertiary = Money,
    background = AppBg,
    onBackground = TextColor,
    surface = CardBg,
    onSurface = TextColor,
    outline = PanelBorder,
    error = Accent2,
)

@Composable
fun BullyTheTrousseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BullyColorScheme, content = content)
}
