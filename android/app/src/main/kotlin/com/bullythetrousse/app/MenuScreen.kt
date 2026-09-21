@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.Beach
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SkinStats

/**
 * Écran d'accueil, porté à l'identique de `#screen-menu` côté web
 * (index.html) : ciel dégradé + voile de nuit/étoiles/lune (`.sky-anim`),
 * carte titre (`.title-card`), pastille d'argent (`.money-pill`), aperçu
 * flottant de la trousse (`#trousse-preview-wrap`), puces de stats
 * (`.stat-chip`), boutons (`.btn`) et rangée des mondes (`.world-card`,
 * avec les cartes Succès/Défis et leur badge `.achv-badge`).
 *
 * Toutes les valeurs (couleurs, tailles, espacements) viennent des règles
 * CSS correspondantes, pas d'une approximation — voir Palette.kt.
 */
@Composable
fun MenuScreen(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onPlay: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenChallenges: () -> Unit,
    onStartVolcanoCinematic: () -> Unit,
    onStartBeachCinematic: () -> Unit,
) {
    var toast by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        SkyAnimation()

        // .menu-wrap : colonne centrée, défilable, padding 24/16/16, gap 10px.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TitleCard()
            MoneyPill(save.money)
            TroussePreview()

            // .stats-row
            FlowRowCentered(gap = 10.dp) {
                StatChip("Puissance", SkinStats.totalPuissance(save).toString())
                StatChip("Vitesse", SkinStats.totalVitesse(save).toString())
                val record = if (save.currentWorld == "plage") save.plageBestDistance else save.bestDistance
                StatChip("Record", "${"%.1f".format(record)} m")
            }

            // .menu-buttons
            FlowRowCentered(gap = 14.dp) {
                GameButton("🚀 Jouer", onClick = onPlay)
                GameButton("🛒 Boutique", secondary = true, onClick = onOpenShop)
                GameButton("🏆 Classement", secondary = true, onClick = onOpenLeaderboard)
                GameButton("👤 Profil", secondary = true, onClick = onOpenProfile)
            }

            WorldsRow(
                save = save,
                onSaveChange = onSaveChange,
                onOpenAchievements = onOpenAchievements,
                onOpenChallenges = onOpenChallenges,
                onStartVolcanoCinematic = onStartVolcanoCinematic,
                onStartBeachCinematic = onStartBeachCinematic,
                onToast = { toast = it },
            )

            toast?.let {
                Text(it, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.title-card` : numéro de version souligné, titre blanc, sous-titre bleu nuit. */
@Composable
private fun TitleCard() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "v10.2.2",
            color = TextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            "🎒 Bully the Trousse",
            color = Color.White,
            fontSize = 36.sp, // clamp(28px, 6vw, 46px)
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "Lance ta trousse le plus loin possible !",
            color = TitleSubtitle,
            fontSize = 14.sp, // clamp(12px, 2.5vw, 15px)
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** `.money-pill` : pastille très arrondie, montant en vert. */
@Composable
private fun MoneyPill(money: Int) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text("💰 $money $", color = Money, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * `#trousse-preview-wrap` + `@keyframes floaty` : l'image monte de 10px et
 * bascule de -3° à +3° en 2,6 s, en boucle (1,3 s par demi-cycle).
 */
@Composable
private fun TroussePreview() {
    val transition = rememberInfiniteTransition(label = "floaty")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatyProgress",
    )
    Image(
        painter = painterResource(R.drawable.trousse_skin_1),
        contentDescription = "trousse",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth(0.38f)
            .aspectRatio(1f)
            .offset(y = (-10).dp * progress)
            .rotate(-3f + 6f * progress),
    )
}

/** `.stat-chip` : libellé en clair, valeur en doré (`.stat-chip b`). */
@Composable
private fun StatChip(label: String, value: String) {
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

/**
 * `.btn` / `.btn.secondary` : coins à 14px, texte 18px très gras, et
 * l'ombre "dure" de 5px en dessous (`box-shadow: 0 5px 0`).
 */
@Composable
private fun GameButton(label: String, secondary: Boolean = false, onClick: () -> Unit) {
    val background = if (secondary) ButtonSecondary else Accent
    val shadow = if (secondary) ButtonSecondaryShadow else ButtonAccentShadow
    val content = if (secondary) Color.White else OnAccent
    val shape = RoundedCornerShape(14.dp)

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 5.dp)
                .clip(shape)
                .background(shadow),
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 30.dp, vertical = 14.dp),
        ) {
            Text(label, color = content, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
        }
    }
}

/**
 * `.worlds-row` : les 4 mondes, avec les cartes "Succès" et "Défis"
 * insérées entre Volcans et Plage (voir buildWorldsRow() côté web).
 */
@Composable
private fun WorldsRow(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenChallenges: () -> Unit,
    onStartVolcanoCinematic: () -> Unit,
    onStartBeachCinematic: () -> Unit,
    onToast: (String) -> Unit,
) {
    fun click(world: World) {
        // Coincé sur la plage : aucun autre monde tant que le bus n'est pas payé.
        if (save.inPlage && world.id != "plage") {
            onToast("🚌 Tu es coincé sur la plage tant que tu n'as pas repris le bus.")
            return
        }
        when (world.id) {
            "plage" -> when {
                !save.hasClaquettes -> onToast("🩴 Achète d'abord la Trousse à Claquettes.")
                !save.plageUnlocked -> onStartBeachCinematic()
                else -> onSaveChange(Beach.enter(save))
            }
            "volcans" -> if (save.volcanUnlocked) {
                onSaveChange(save.copy(currentWorld = "volcans"))
            } else {
                val cooldownMs = save.volcanFailedUntil - System.currentTimeMillis()
                if (cooldownMs > 0) {
                    onToast("🌋 Le volcan gronde encore... réessaie dans ${cooldownMs / 60_000} min ${(cooldownMs % 60_000) / 1000} s")
                } else {
                    onStartVolcanoCinematic()
                }
            }
            "cour" -> onSaveChange(save.copy(currentWorld = "cour"))
            else -> onToast("🔒 ${world.name} est verrouillé.")
        }
    }

    val claimableChallenges = save.dailyChallenges.count { !it.claimed && it.progress >= it.target }

    FlowRowCentered(gap = 10.dp, modifier = Modifier.widthIn(max = 520.dp)) {
        WorldCard(WORLD_COUR, playable = true, selected = save.currentWorld == "cour") { click(WORLD_COUR) }
        WorldCard(WORLD_VOLCANS, playable = save.volcanUnlocked, selected = save.currentWorld == "volcans" && save.volcanUnlocked) {
            click(WORLD_VOLCANS)
        }
        WorldCard(
            World("succes", "Succès", "🏆"),
            playable = true,
            selected = false,
            accentBorder = true,
            badge = "${save.unlockedAchievements.size}/${Achievements.ALL.size}",
            onClick = onOpenAchievements,
        )
        WorldCard(
            World("defis", "Défis", "📅"),
            playable = true,
            selected = false,
            accentBorder = true,
            badge = "$claimableChallenges/${save.dailyChallenges.size.coerceAtLeast(3)}",
            onClick = onOpenChallenges,
        )
        WorldCard(WORLD_PLAGE, playable = save.plageUnlocked, selected = save.currentWorld == "plage" && save.plageUnlocked) {
            click(WORLD_PLAGE)
        }
        WorldCard(WORLD_VILLE, playable = false, selected = false) { click(WORLD_VILLE) }
    }
}

/**
 * `.world-card` : 108px de large, emoji 26px, nom 12px. Verrouillée = 55 %
 * d'opacité ; sélectionnée (ou carte Succès/Défis) = bordure dorée, avec
 * un `.achv-badge` en haut à droite quand il y a un compteur.
 */
@Composable
private fun WorldCard(
    world: World,
    playable: Boolean,
    selected: Boolean,
    accentBorder: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box {
        Column(
            modifier = Modifier
                .width(108.dp)
                .alpha(if (playable) 1f else 0.55f)
                .clip(shape)
                .background(PanelBg)
                .border(2.dp, if (selected || accentBorder) Accent else PanelBorder, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(world.emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
            Text(
                world.name,
                color = TextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // .achv-badge : top:-8px; right:-6px
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-8).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(badge, color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * `.sky-anim` : occupe les 60 % supérieurs de l'écran. En thème sombre (le
 * thème par défaut du site), un voile nocturne, 6 étoiles et la lune.
 */
@Composable
private fun SkyAnimation() {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
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
private fun PositionedAt(xFraction: Float, yFraction: Float, content: @Composable () -> Unit) {
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
private fun FlowRowCentered(gap: Dp, modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

private data class World(val id: String, val name: String, val emoji: String)

/** Portage du tableau `WORLDS` côté web, dans le même ordre. */
private val WORLD_COUR = World("cour", "Cour d'école", "🏫")
private val WORLD_VOLCANS = World("volcans", "Volcans", "🌋")
private val WORLD_PLAGE = World("plage", "Plage", "🏖️")
private val WORLD_VILLE = World("ville", "Ville", "🏙️")

/** Positions des 6 `.star` du web, en fraction de la zone de ciel. */
private val STAR_POSITIONS = listOf(
    0.15f to 0.18f,
    0.28f to 0.08f,
    0.70f to 0.14f,
    0.82f to 0.28f,
    0.50f to 0.06f,
    0.90f to 0.10f,
)
