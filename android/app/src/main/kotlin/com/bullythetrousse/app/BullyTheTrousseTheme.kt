package com.bullythetrousse.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème Material adossé à la palette du site (voir Palette.kt). Les écrans
 * portés dessinent surtout leurs couleurs à la main (comme le CSS) ; ce
 * thème sert aux composants Material restants — les champs de saisie
 * surtout, dont le texte doit rester lisible en thème clair comme en sombre.
 *
 * Recalculé à chaque changement de thème (voir [AppTheme]) : une constante
 * garderait les couleurs sombres, et le texte des champs deviendrait blanc
 * sur blanc en thème clair.
 */
private fun bullyColorScheme(light: Boolean): ColorScheme {
    val base = if (light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = Accent,
        onPrimary = OnAccent,
        secondary = ButtonSecondary,
        onSecondary = Color.White,
        tertiary = Money,
        background = AppBg,
        onBackground = TextColor,
        surface = CardBg,
        onSurface = TextColor,
        onSurfaceVariant = TextDim,
        outline = PanelBorder,
        error = Accent2,
    )
}

@Composable
fun BullyTheTrousseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = bullyColorScheme(AppTheme.isLight), content = content)
}
