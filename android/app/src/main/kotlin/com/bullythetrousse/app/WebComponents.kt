@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * Les "classes CSS" du site, portées une fois et réutilisées par tous les
 * écrans — exactement comme le web où `.btn`, `.money-pill`, `.sky-anim`
 * servent au menu, au jeu et à la boutique. Les valeurs viennent des règles
 * CSS d'index.html ; les couleurs viennent de Palette.kt.
 */

/**
 * `.btn` / `.btn.secondary` / `.btn.small` : coins arrondis, texte très
 * gras, et l'ombre "dure" de 5px en dessous (`box-shadow: 0 5px 0`).
 */
@Composable
fun GameButton(
    label: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (secondary) ButtonSecondary else Accent
    val shadowColor = if (secondary) ButtonSecondaryShadow else ButtonAccentShadow
    val content = if (secondary) Color.White else OnAccent
    // .btn : 14px de rayon, 14/30 de padding, 18px ; .btn.small : 10px, 10/18, 14px
    val shape = RoundedCornerShape(if (small) 10.dp else 14.dp)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 5.dp)
                .clip(shape)
                .background(shadowColor),
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(background)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (small) 18.dp else 30.dp,
                    vertical = if (small) 10.dp else 14.dp,
                ),
        ) {
            Text(
                label,
                color = content,
                fontSize = if (small) 14.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * `.money-pill` : pastille très arrondie sur fond panneau. Le texte est en
 * vert "money" par défaut, mais l'écran de jeu réutilise la même pastille
 * en `--text` pour la distance (voir `#game-distance`).
 */
@Composable
fun MoneyPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Money,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(
                horizontal = if (small) 14.dp else 20.dp,
                vertical = if (small) 6.dp else 8.dp,
            ),
    ) {
        Text(text, color = color, fontSize = if (small) 14.sp else 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** `.stat-chip` : libellé en clair, valeur en doré (`.stat-chip b`). */
@Composable
fun StatChip(label: String, value: String) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label ", color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** `.hint-text` : consigne en bas de l'écran de jeu, blanche et très grasse. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        fontSize = 17.sp, // clamp(14px, 3vw, 20px)
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
    )
}

/**
 * `.sky-anim` : le ciel animé, qui occupe le haut de l'écran (jusqu'à 60 %
 * sur le menu, 68 % sur l'écran de jeu). En thème sombre — celui par défaut
 * du site — un voile nocturne, 6 étoiles et la lune.
 */
@Composable
fun SkyAnimation(heightFraction: Float) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(heightFraction)) {
        // .sky-anim .tint
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xBF060A22), Color(0x4D121A40))),
            ),
        )
        for ((xFraction, yFraction) in STAR_POSITIONS) {
            PositionedAt(xFraction, yFraction) { Star() }
        }
        PositionedAt(xFraction = 0.68f, yFraction = 0.10f) { Moon() }
    }
}

/** `.sky-anim .star` : 3px, blanche, halo `0 0 4px 1px rgba(255,255,255,0.8)`. */
@Composable
private fun Star() {
    Canvas(modifier = Modifier.size(10.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xCCFFFFFF), Color.Transparent),
                center = center,
                radius = size.minDimension / 2f,
            ),
            radius = size.minDimension / 2f,
            center = center,
        )
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 1.5.dp.toPx(), center = center)
    }
}

/** `.sky-anim .moon` : 48px, dégradé radial décalé à 35 %/35 %, et son halo. */
@Composable
private fun Moon() {
    Canvas(modifier = Modifier.size(96.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val moonRadius = 24.dp.toPx()
        // box-shadow 0 0 32px 8px rgba(220,225,255,0.45)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x73DCE1FF), Color.Transparent),
                center = center,
                radius = moonRadius * 2f,
            ),
            radius = moonRadius * 2f,
            center = center,
        )
        // radial-gradient(circle at 35% 35%, #ffffff, #e6ebf7 60%, #b9c2da 100%)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to Color.White, 0.6f to Color(0xFFE6EBF7), 1f to Color(0xFFB9C2DA)),
                center = Offset(center.x - moonRadius * 0.3f, center.y - moonRadius * 0.3f),
                radius = moonRadius,
            ),
            radius = moonRadius,
            center = center,
        )
    }
}

/** Équivalent de `position:absolute; left:X%; top:Y%` dans la boîte parente. */
@Composable
fun PositionedAt(xFraction: Float, yFraction: Float, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.fillMaxHeight(yFraction))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.fillMaxWidth(xFraction))
            content()
        }
    }
}

/** Équivalent de `display:flex; flex-wrap:wrap; justify-content:center; gap:N`. */
@Composable
fun FlowRowCentered(gap: Dp, modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Positions des 6 `.star` du web, en fraction de la zone de ciel. */
private val STAR_POSITIONS = listOf(
    0.15f to 0.18f,
    0.28f to 0.08f,
    0.70f to 0.14f,
    0.82f to 0.28f,
    0.50f to 0.06f,
    0.90f to 0.10f,
)
