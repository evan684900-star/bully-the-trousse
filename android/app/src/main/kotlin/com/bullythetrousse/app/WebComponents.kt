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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

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

/**
 * `.links-btn` + `.corner-icons-right` : la barre flottante présente sur
 * TOUS les écrans du site (position: fixed) — "Mes liens" en bas à gauche,
 * son et réglages en bas à droite, en pastilles de 42px.
 */
@Composable
fun BottomBar(
    musicMuted: Boolean,
    onOpenLinks: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // env(safe-area-inset-*) côté web : la barre ne doit pas passer sous la
    // barre de navigation du téléphone.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(10.dp),
    ) {
        // .links-btn : pastille allongée, texte "text-dim"
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .height(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PanelBg)
                .border(2.dp, PanelBorder, RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenLinks)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔗 Mes liens", color = TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        // .corner-icons-right : deux ronds de 42px
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(if (musicMuted) "🔇" else "🔊", onToggleMute)
            RoundIconButton("⚙️", onOpenSettings)
        }
    }
}

/** `.mute-btn` / `.theme-btn` : rond de 42px, fond panneau, bordure. */
@Composable
private fun RoundIconButton(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 18.sp)
    }
}

/**
 * `.tabs` + `.tab-btn` : le rail d'onglets de la boutique. L'onglet actif
 * porte la pastille dorée qui glisse derrière lui côté web ; ici elle est
 * simplement posée sous l'onglet sélectionné.
 */
@Composable
fun ShopTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) OnAccent else TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * `.upgrade-card` / `.skin-card` : icône à gauche (46px), titre + sous-titre
 * au centre, bouton d'action à droite. Fond `--card-bg`, bordure 2px,
 * coins à 14px.
 */
@Composable
fun ShopCard(
    title: String,
    description: String,
    levelBadge: String? = null,
    leading: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // .upgrade-card .icon : colonne de 46px, contenu centré
        Box(modifier = Modifier.width(46.dp), contentAlignment = Alignment.Center) { leading() }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (levelBadge != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Accent)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(levelBadge, color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (description.isNotEmpty()) {
                Text(
                    description,
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        action()
    }
}

/**
 * `.toast` : petit message temporaire en bas de l'écran ("Pas assez
 * d'argent !", etc.). Fond volontairement toujours sombre, quel que soit le
 * thème, et texte blanc en dur — comme le commente le CSS du site. Il
 * remonte à 70px du bas pour ne pas passer derrière la barre flottante.
 */
@Composable
fun Toast(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(2200)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 70.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xF2141824))
                .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * `.coin-popup` : le bonus de la Trousse Pièce prend tout l'écran d'un coup
 * quand le multiplicateur se déclenche (voir showCoinPopup côté web), puis
 * disparaît. Le montant est écrit en très gros doré sur un voile sombre.
 */
@Composable
fun CoinPopup(multiplier: Double?, jackpot: Boolean, onDismiss: () -> Unit) {
    if (multiplier == null) return
    LaunchedEffect(multiplier, jackpot) {
        delay(1600)
        onDismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x990A0802)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🪙", fontSize = 90.sp)
            Text(
                "x${formatMultiplier(multiplier)}",
                color = Accent,
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
            )
            if (jackpot) {
                Text("JACKPOT !", color = Accent2, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** "x2" plutôt que "x2.0", mais "x1.6" garde sa décimale, comme le site. */
private fun formatMultiplier(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Positions des 6 `.star` du web, en fraction de la zone de ciel. */
private val STAR_POSITIONS = listOf(
    0.15f to 0.18f,
    0.28f to 0.08f,
    0.70f to 0.14f,
    0.82f to 0.28f,
    0.50f to 0.06f,
    0.90f to 0.10f,
)
