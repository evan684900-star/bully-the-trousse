@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.Beach
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SkinStats

/**
 * Écran d'accueil, porté à l'identique de `#screen-menu` (index.html) :
 * `.sky-anim`, `.title-card`, `.money-pill`, `#trousse-preview-wrap`,
 * `.stats-row`, `.menu-buttons` et `.worlds-row` (avec les cartes
 * Succès/Défis et leur `.achv-badge`), dans cet ordre.
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
    onOpenChangelog: () -> Unit,
) {
    var toast by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        SkyAnimation(heightFraction = 0.6f) // #screen-menu .sky-anim { bottom: 40% }

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
            TitleCard(onOpenChangelog = onOpenChangelog)
            MoneyPill("💰 ${save.money} $")
            TroussePreview(equippedSkin = save.equippedSkin)

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

        }

        Toast(message = toast, onDismiss = { toast = null })
    }
}

/** `.title-card` : numéro de version souligné, titre blanc, sous-titre bleu nuit. */
@Composable
private fun TitleCard(onOpenChangelog: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "v10.2.2",
            color = TextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onOpenChangelog).padding(bottom = 2.dp),
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

/**
 * `#trousse-preview-wrap` + `@keyframes floaty` : l'image monte de 10px et
 * bascule de -3° à +3° en 2,6 s, en boucle (1,3 s par demi-cycle).
 */
@Composable
private fun TroussePreview(equippedSkin: String) {
    val transition = rememberInfiniteTransition(label = "floaty")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatyProgress",
    )
    TrousseSprite(
        skinId = equippedSkin,
        contentDescription = "trousse",
        modifier = Modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth(0.38f)
            .aspectRatio(1f)
            .offset(y = (-10).dp * progress)
            .rotate(-3f + 6f * progress),
    )
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

    val claimable = save.dailyChallenges.count { !it.claimed && it.progress >= it.target }

    FlowRowCentered(gap = 10.dp, modifier = Modifier.widthIn(max = 520.dp)) {
        WorldCard(WORLD_COUR, playable = true, selected = save.currentWorld == "cour") { click(WORLD_COUR) }
        WorldCard(
            WORLD_VOLCANS,
            playable = save.volcanUnlocked,
            selected = save.currentWorld == "volcans" && save.volcanUnlocked,
        ) { click(WORLD_VOLCANS) }
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
            badge = "$claimable/${save.dailyChallenges.size.coerceAtLeast(3)}",
            onClick = onOpenChallenges,
        )
        WorldCard(
            WORLD_PLAGE,
            playable = save.plageUnlocked,
            selected = save.currentWorld == "plage" && save.plageUnlocked,
        ) { click(WORLD_PLAGE) }
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

private data class World(val id: String, val name: String, val emoji: String)

/** Portage du tableau `WORLDS` côté web, dans le même ordre. */
private val WORLD_COUR = World("cour", "Cour d'école", "🏫")
private val WORLD_VOLCANS = World("volcans", "Volcans", "🌋")
private val WORLD_PLAGE = World("plage", "Plage", "🏖️")
private val WORLD_VILLE = World("ville", "Ville", "🏙️")
