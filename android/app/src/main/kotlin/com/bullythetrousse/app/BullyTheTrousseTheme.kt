package com.bullythetrousse.app

import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
        // Le primaire colore la bordure et le curseur des champs de saisie :
        // en thème clair, l'or pâle disparaîtrait sur le blanc.
        primary = if (light) AccentText else Accent,
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

/**
 * Les arrondis du jeu (`.btn.small` 10px, `.btn` et cartes 14px, boîtes
 * 18px) appliqués aux composants Material restants : sans ça, les champs de
 * saisie gardent les coins de 4dp de Material et détonnent à côté des
 * boutons du jeu.
 */
private val BullyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

@Composable
fun BullyTheTrousseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = bullyColorScheme(AppTheme.isLight), shapes = BullyShapes, content = content)
}
