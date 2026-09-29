package com.bullythetrousse.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Le pseudo d'un joueur, partout où il apparaît (classement, profil, listes
 * d'abonnés, cadeaux) : en arc-en-ciel qui défile s'il joue sur l'app
 * Android — `.rainbow-name` côté site, avec les mêmes couleurs.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun PlayerName(
    name: String,
    android: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = TextColor,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (!android) {
        Text(
            name,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    // Un arc-en-ciel complet tous les RAINBOW_PERIOD, répété : chaque nom,
    // même court, montre toutes les couleurs, et le défilement boucle sans saut.
    val period = with(LocalDensity.current) { RAINBOW_PERIOD.toPx() }
    val transition = rememberInfiniteTransition(label = "rainbowName")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbowNameShift",
    )
    val colors = if (AppTheme.isLight) RAINBOW_LIGHT else RAINBOW_DARK
    val offset = shift * period
    val brush = Brush.linearGradient(
        colors = colors + colors.first(),
        start = Offset(-offset, 0f),
        end = Offset(period - offset, 0f),
        tileMode = TileMode.Repeated,
    )
    Text(
        name,
        style = TextStyle(brush = brush, fontSize = fontSize, fontWeight = FontWeight.ExtraBold),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Longueur d'un arc-en-ciel complet. */
private val RAINBOW_PERIOD = 120.dp

/** Couleurs de `.rainbow-name` côté site (thème sombre). */
private val RAINBOW_DARK = listOf(
    Color(0xFFFF4D4D), Color(0xFFFF9F40), Color(0xFFFFE04D), Color(0xFF5CFF7A),
    Color(0xFF4DD2FF), Color(0xFF8A6BFF), Color(0xFFFF5CE1),
)

/** Thème clair : teintes plus soutenues, sinon le jaune disparaît sur le blanc. */
private val RAINBOW_LIGHT = listOf(
    Color(0xFFE0202A), Color(0xFFE8710A), Color(0xFFC99A00), Color(0xFF1F9D3A),
    Color(0xFF0B86C4), Color(0xFF5B3FD1), Color(0xFFC2188F),
)
