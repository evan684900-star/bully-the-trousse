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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import com.bullythetrousse.core.SfxCatalog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Écran d'accueil, porté à l'identique de `#screen-menu` (index.html) :
 * `.sky-anim`, `.title-card`, `.money-pill`, `#trousse-preview-wrap`,
 * `.stats-row`, `.menu-buttons` et `.worlds-row` (avec les cartes
 * Succès/Défis et leur `.achv-badge`), dans cet ordre.
 */
@Composable
internal fun MenuScreen(
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
    onOpenCheats: () -> Unit,
    /** Une modale du menu demandée de l'extérieur (panneau des triches). */
    requestedDialog: MenuDialog? = null,
    onRequestedDialogShown: () -> Unit = {},
) {
    var toast by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<MenuDialog?>(null) }
    val sfx = LocalSfx.current
    LaunchedEffect(requestedDialog) {
        if (requestedDialog != null) {
            dialog = requestedDialog
            onRequestedDialogShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        // #screen-menu .sky-anim { bottom: 40% } — jour/nuit selon save.theme
        SkyAnimation(heightFraction = 0.6f, night = save.theme != "light", world = save.currentWorld)

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
            TitleCard(onOpenChangelog = onOpenChangelog, onOpenCheats = onOpenCheats)
            MoneyPill("💰 ${save.money} $")
            // Toucher la trousse ouvre sa fiche (openTrousseModal()).
            TroussePreview(equippedSkin = save.equippedSkin, onClick = { dialog = MenuDialog.TrousseInfo })

            // .stats-row
            FlowRowCentered(gap = 10.dp) {
                StatChip(tr("statPuissance"), SkinStats.totalPuissance(save).toString())
                StatChip(tr("statVitesse"), SkinStats.totalVitesse(save).toString())
                val record = if (save.currentWorld == "plage") save.plageBestDistance else save.bestDistance
                StatChip(tr("statRecord"), "${"%.1f".format(record)} m")
            }

            // .menu-buttons
            FlowRowCentered(gap = 14.dp) {
                GameButton(tr("btnPlay")) {
                    // Trousse cassée : impossible de jouer avant réparation.
                    if (save.durability <= 0) {
                        sfx.play(SfxCatalog.ERROR)
                        dialog = MenuDialog.RepairBroken
                    } else {
                        onPlay()
                    }
                }
                GameButton(tr("btnShop"), secondary = true, onClick = onOpenShop)
                GameButton(tr("btnLeaderboard"), secondary = true, onClick = onOpenLeaderboard)
                GameButton(tr("btnProfile"), secondary = true, onClick = onOpenProfile)
            }

            WorldsRow(
                save = save,
                onSaveChange = onSaveChange,
                onOpenAchievements = onOpenAchievements,
                onOpenChallenges = onOpenChallenges,
                onStartVolcanoCinematic = onStartVolcanoCinematic,
                onStartBeachCinematic = onStartBeachCinematic,
                onShowDialog = { dialog = it },
                onToast = { toast = it },
            )

            // #menu-help : proposé après un échec dans la cinématique du volcan.
            if (save.volcanHelpAvailable) {
                Text(
                    tr("menuHelp"),
                    color = TextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { dialog = MenuDialog.Help }.padding(top = 6.dp, bottom = 40.dp),
                )
            }
        }

        MenuDialogs(
            dialog = dialog,
            save = save,
            onSaveChange = onSaveChange,
            onOpenShop = onOpenShop,
            onStartVolcanoCinematic = onStartVolcanoCinematic,
            onStartBeachCinematic = onStartBeachCinematic,
            onDismiss = { dialog = null },
        )
        Toast(message = toast, onDismiss = { toast = null })
    }
}

/** Les petites modales du menu (voir `#trousse-modal`, `#help-modal`...). */
internal enum class MenuDialog { TrousseInfo, Help, RepairBroken, VolcanReplay, PlageReplay }

@Composable
private fun MenuDialogs(
    dialog: MenuDialog?,
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onOpenShop: () -> Unit,
    onStartVolcanoCinematic: () -> Unit,
    onStartBeachCinematic: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sfx = LocalSfx.current
    when (dialog) {
        null -> Unit

        // #trousse-modal : durabilité (avec sa barre), nom, temps de jeu, record.
        MenuDialog.TrousseInfo -> InfoDialog(title = tr("trousseTitle"), onDismiss = onDismiss) {
            val max = SkinStats.maxDurability(save)
            InfoLine(tr("labelDurability"), "${save.durability} / $max")
            DurabilityBar(fraction = if (max > 0) save.durability.toFloat() / max else 0f)
            InfoLine(tr("labelName"), save.pseudo.ifBlank { "Trousse" })
            InfoLine(tr("labelPlaytime"), formatPlayTime(save.playTime))
            val best = if (save.currentWorld == "plage") save.plageBestDistance else save.bestDistance
            InfoLine(tr("labelBest"), "${"%.1f".format(best)} m")
        }

        // #help-modal : comment réussir l'esquive des roches.
        MenuDialog.Help -> InfoDialog(
            title = tr("helpTitle"),
            onDismiss = onDismiss,
            buttons = { GameButton(tr("btnUnderstood"), modifier = Modifier.fillMaxWidth(), onClick = onDismiss) },
        ) {
            DialogText(tr("helpText"), color = TextColor)
        }

        // #repair-broken-modal : 0 de durabilité, direction la boutique.
        MenuDialog.RepairBroken -> InfoDialog(
            title = tr("repairBrokenTitle"),
            onDismiss = onDismiss,
            buttons = {
                GameButton(tr("btnGoRepair"), modifier = Modifier.fillMaxWidth()) {
                    onDismiss()
                    onOpenShop()
                }
            },
        ) {
            DialogText(tr("repairBrokenText"), color = TextColor)
        }

        // #volcan-replay-modal : revivre la cinématique, ou y aller directement.
        MenuDialog.VolcanReplay -> ReplayDialog(
            title = tr("volcanReplayTitle"),
            text = tr("volcanReplayText"),
            yes = tr("volcanReplayYes"),
            no = tr("volcanReplayNo"),
            onYes = {
                onDismiss()
                onStartVolcanoCinematic()
            },
            onNo = {
                onDismiss()
                onSaveChange(save.copy(currentWorld = "volcans"))
                sfx.play(SfxCatalog.CHARGE)
            },
            onDismiss = onDismiss,
        )

        // #plage-replay-modal : revivre le voyage, ou repartir directement.
        MenuDialog.PlageReplay -> ReplayDialog(
            title = tr("plageReplayTitle"),
            text = tr("plageReplayText"),
            yes = tr("plageReplayYes"),
            no = tr("plageReplayNo"),
            onYes = {
                onDismiss()
                onStartBeachCinematic()
            },
            onNo = {
                onDismiss()
                onSaveChange(Beach.enter(save))
                sfx.play(SfxCatalog.CHARGE)
            },
            onDismiss = onDismiss,
        )
    }
}

/** `.replay-buttons` : deux boutons côte à côte, « Refaire » / « Juste y aller ». */
@Composable
private fun ReplayDialog(
    title: String,
    text: String,
    yes: String,
    no: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onDismiss: () -> Unit,
) {
    InfoDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(yes, modifier = Modifier.weight(1f), onClick = onYes)
                GameButton(no, secondary = true, modifier = Modifier.weight(1f), onClick = onNo)
            }
        },
    ) {
        DialogText(text, color = TextColor)
    }
}

/** `.info-line` : libellé à gauche, valeur en gras à droite, filet dessous. */
@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextColor, fontSize = 14.sp)
            Text(value, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x2E808080)))
    }
}

/** `.dura-bar` : barre dégradée rouge → vert, remplie selon la durabilité. */
@Composable
private fun DurabilityBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x4D000000)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(12.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFFFF6B6B), Color(0xFF6BFFB0)))),
        )
    }
}

/** `formatPlayTime(sec)` : « 2 h 5 min », ou « 12 min » sous une heure. */
private fun formatPlayTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

/** `.title-card` : numéro de version souligné, titre blanc, sous-titre bleu nuit. */
@Composable
private fun TitleCard(onOpenChangelog: () -> Unit, onOpenCheats: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "v10.2.2",
            color = TextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            textDecoration = TextDecoration.Underline,
            // Toucher : journal des changements. Appui long : les triches
            // (window.cheats du site, dans sa console — ici tout aussi discrètes).
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpenChangelog() }, onLongPress = { onOpenCheats() })
                }
                .padding(bottom = 2.dp),
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
            tr("menuSubtitle"),
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
private fun TroussePreview(equippedSkin: String, onClick: () -> Unit) {
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
            .rotate(-3f + 6f * progress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
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
    onShowDialog: (MenuDialog) -> Unit,
    onToast: (String) -> Unit,
) {
    val sfx = LocalSfx.current
    val stuck = tr("stuckOnBeach")
    val needsClaquettes = tr("plageNeedsClaquettes")
    val volcanoRetry = tr("volcanoRetry")
    val comingSoon = tr("worldLocked")

    fun refuse(message: String) {
        onToast(message)
        sfx.play(SfxCatalog.ERROR)
    }

    fun click(world: World) {
        // Coincé sur la plage : aucun autre monde tant que le bus n'est pas payé.
        if (save.inPlage && world.id != "plage") return refuse(stuck)
        when (world.id) {
            // handlePlageCardClick() : déjà sur place, rien à faire ; sinon les
            // claquettes d'abord, puis la cinématique, puis « y retourner ? ».
            "plage" -> when {
                save.inPlage -> Unit
                !save.hasClaquettes -> refuse(needsClaquettes)
                !save.plageUnlocked -> onStartBeachCinematic()
                else -> onShowDialog(MenuDialog.PlageReplay)
            }
            "volcans" -> if (save.volcanUnlocked) {
                // Déjà débloqué : on propose de refaire la cinématique.
                onShowDialog(MenuDialog.VolcanReplay)
            } else {
                // tryStartVolcanoCinematic() : délai après un échec.
                val left = save.volcanFailedUntil - System.currentTimeMillis()
                if (left > 0) {
                    refuse("$volcanoRetry${left / 60_000} min ${(left % 60_000) / 1000} s")
                } else {
                    onStartVolcanoCinematic()
                }
            }
            "cour" -> {
                onSaveChange(save.copy(currentWorld = "cour"))
                sfx.play(SfxCatalog.CHARGE)
            }
            else -> refuse("🔒 ${world.name}$comingSoon")
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
            World("succes", tr("achvCardName"), "🏆"),
            playable = true,
            selected = false,
            accentBorder = true,
            badge = "${save.unlockedAchievements.size}/${Achievements.ALL.size}",
            onClick = onOpenAchievements,
        )
        WorldCard(
            World("defis", tr("challengesCardName"), "📅"),
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
