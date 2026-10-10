@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
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
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.Ville
import com.bullythetrousse.core.VilleEntry
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
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
    /** Entrée dans le monde Ville (vol depuis la carte, ou "Jouer" quand on y est coincé). */
    onEnterVille: (VilleEntry) -> Unit = {},
    /** Message à afficher en arrivant sur le menu ("✈️ Bon vol !" après un départ de la Ville). */
    initialToast: String? = null,
    onInitialToastShown: () -> Unit = {},
) {
    var toast by remember { mutableStateOf(initialToast) }
    var dialog by remember { mutableStateOf<MenuDialog?>(null) }
    val sfx = LocalSfx.current
    LaunchedEffect(initialToast) { if (initialToast != null) onInitialToastShown() }
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

            // Propre à l'app : un voile léger regroupe stats, boutons et cartes,
            // pour qu'ils restent lisibles quand ils défilent par-dessus la
            // ligne d'horizon du fond (fixe, lui).
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MenuScrim)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // .stats-row
                FlowRowCentered(gap = 10.dp) {
                    StatChip(tr("statPuissance"), SkinStats.totalPuissance(save).toString())
                    StatChip(tr("statVitesse"), SkinStats.totalVitesse(save).toString())
                    val record = when (save.currentWorld) {
                        "plage" -> save.plageBestDistance
                        Ville.WORLD_ID -> save.villeBestDistance
                        else -> save.bestDistance
                    }
                    StatChip(tr("statRecord"), "${"%.1f".format(record)} m")
                }

                // .menu-buttons, réorganisé pour Android : « Jouer » en gros bouton
                // pleine largeur (l'action principale), les trois autres en
                // grille de 3 colonnes plutôt qu'un FlowRow au retour à la ligne
                // imprévisible.
                GameButton(tr("btnPlay"), large = true, modifier = Modifier.fillMaxWidth()) {
                    when {
                        // En Ville, "Jouer" ramène à la réception de la Tour.
                        save.currentWorld == Ville.WORLD_ID && save.inVille -> onEnterVille(VilleEntry.RECEPTION)
                        // Trousse cassée : impossible de jouer avant réparation.
                        save.durability <= 0 -> {
                            sfx.play(SfxCatalog.ERROR)
                            dialog = MenuDialog.RepairBroken
                        }
                        else -> onPlay()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MenuTile(tr("btnShop"), Modifier.weight(1f).fillMaxHeight(), onClick = onOpenShop)
                    MenuTile(tr("btnLeaderboard"), Modifier.weight(1f).fillMaxHeight(), onClick = onOpenLeaderboard)
                    MenuTile(tr("btnProfile"), Modifier.weight(1f).fillMaxHeight(), onClick = onOpenProfile)
                }

                WorldsRow(
                    save = save,
                    onSaveChange = onSaveChange,
                    onOpenAchievements = onOpenAchievements,
                    onOpenChallenges = onOpenChallenges,
                    onStartVolcanoCinematic = onStartVolcanoCinematic,
                    onStartBeachCinematic = onStartBeachCinematic,
                    onEnterVille = onEnterVille,
                    onShowDialog = { dialog = it },
                    onToast = { toast = it },
                )
            }

            // #menu-help : proposé après un échec dans la cinématique du volcan.
            if (save.volcanHelpAvailable) {
                Text(
                    tr("menuHelp"),
                    color = TextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 40.dp)
                        .clickable { dialog = MenuDialog.Help }
                        .heightIn(min = 48.dp)
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp),
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
            InfoLine(tr("labelName"), save.pseudo.ifBlank { tr("app.trousseDefaultName") }, rainbow = save.playedOnAndroid)
            InfoLine(tr("labelPlaytime"), formatPlayTime(save.playTime))
            val best = when (save.currentWorld) {
                "plage" -> save.plageBestDistance
                Ville.WORLD_ID -> save.villeBestDistance
                else -> save.bestDistance
            }
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
private fun InfoLine(label: String, value: String, rainbow: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextColor, fontSize = 14.sp)
            PlayerName(value, android = rainbow, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
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

/**
 * `.title-card` : numéro de version souligné, titre blanc, sous-titre.
 *
 * Deux écarts volontaires avec le site, pour la lisibilité sur téléphone :
 * le titre blanc a un contour sombre et une ombre (blanc sur le ciel clair
 * ne fait que 1,2:1 de contraste), et le sous-titre est posé sur une
 * pastille (le bleu nuit du site se perd sur le ciel assombri de la nuit).
 */
@Composable
private fun TitleCard(onOpenChangelog: () -> Unit, onOpenCheats: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Toucher : journal des changements. Appui long : les triches
        // (window.cheats du site, dans sa console — ici tout aussi discrètes).
        // Zone tactile d'au moins 48dp autour du petit numéro.
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpenChangelog() }, onLongPress = { onOpenCheats() })
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "v$GAME_VERSION",
                color = TextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textDecoration = TextDecoration.Underline,
            )
        }
        OutlinedTitle("🎒 Bully the Trousse")
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PanelBg)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                tr("menuSubtitle"),
                color = TextColor,
                fontSize = 14.sp, // clamp(12px, 2.5vw, 15px)
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Le titre du menu : blanc, avec un contour sombre dessiné en dessous et une
 * ombre portée. Sa taille suit la police système, mais plafonnée à +30 % :
 * au-delà, « Bully the Trousse » passerait sur trois lignes.
 */
// drawStyle était expérimental dans les premières versions de Compose ;
// l'opt-in est sans effet (simple avertissement) là où il est stable.
@OptIn(ExperimentalTextApi::class)
@Composable
private fun OutlinedTitle(text: String) {
    val fontScale = LocalDensity.current.fontScale
    val size = (TITLE_SIZE_SP * fontScale.coerceAtMost(1.3f) / fontScale).sp
    val base = TextStyle(
        fontSize = size, // clamp(28px, 6vw, 46px)
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
    )
    Box(contentAlignment = Alignment.Center) {
        Text(
            text,
            style = base.copy(
                color = TitleOutline,
                drawStyle = Stroke(width = 7f, join = StrokeJoin.Round),
                shadow = Shadow(color = Color(0x80000000), offset = Offset(0f, 4f), blurRadius = 8f),
            ),
        )
        Text(text, style = base.copy(color = Color.White))
    }
}

private const val TITLE_SIZE_SP = 36f

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
    onEnterVille: (VilleEntry) -> Unit,
    onShowDialog: (MenuDialog) -> Unit,
    onToast: (String) -> Unit,
) {
    val sfx = LocalSfx.current
    val stuck = tr("stuckOnBeach")
    val needsClaquettes = tr("plageNeedsClaquettes")
    val volcanoRetry = tr("volcanoRetry")
    val villeStuck = tr("villeStuck")
    val villeLocked = tr("villeLockedHint")

    fun refuse(message: String) {
        onToast(message)
        sfx.play(SfxCatalog.ERROR)
    }

    fun click(world: World) {
        // Coincé en Ville : seul un vol au départ de l'aéroport permet d'en sortir.
        if (save.inVille && world.id != Ville.WORLD_ID) return refuse(villeStuck)
        if (world.id == Ville.WORLD_ID) {
            when {
                save.inVille -> Unit
                !save.villeUnlocked -> refuse(villeLocked)
                save.inPlage -> refuse(stuck)
                else -> onEnterVille(VilleEntry.FLY_IN)
            }
            return
        }
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
        }
    }

    val claimable = save.dailyChallenges.count { !it.claimed && it.progress >= it.target }

    // Délai après un échec au volcan : le compte à rebours de la carte se
    // met à jour chaque seconde, tant qu'il court.
    val now by produceState(System.currentTimeMillis(), save.volcanFailedUntil) {
        while (value < save.volcanFailedUntil) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val volcanoLeft = save.volcanFailedUntil - now
    val volcanoLock = when {
        save.volcanUnlocked -> null
        volcanoLeft > 0 -> tr("app.lockCooldown", "time" to "${volcanoLeft / 60_000} min ${(volcanoLeft % 60_000) / 1000} s")
        else -> tr("app.lockVolcan")
    }
    val plageLock = when {
        save.plageUnlocked -> null
        !save.hasClaquettes -> tr("app.lockClaquettes")
        else -> tr("app.lockTapToGo")
    }
    val villeLock = if (save.villeUnlocked) null else tr("app.lockVille")

    // Grille de 3 colonnes (2 lignes) plutôt que le flex-wrap du site : à
    // 108px de large, les 6 cartes tombaient sur 3 lignes de 2 sur un petit
    // téléphone. Ordre du site conservé.
    Column(
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GridRow {
            WorldCard(WORLD_COUR, cardModifier(), selected = save.currentWorld == "cour") { click(WORLD_COUR) }
            WorldCard(
                WORLD_VOLCANS,
                cardModifier(),
                lockHint = volcanoLock,
                selected = save.currentWorld == "volcans" && save.volcanUnlocked,
            ) { click(WORLD_VOLCANS) }
            WorldCard(
                World("succes", tr("achvCardName"), "🏆"),
                cardModifier(),
                accentBorder = true,
                badge = "${save.unlockedAchievements.size}/${Achievements.ALL.size}",
                onClick = onOpenAchievements,
            )
        }
        GridRow {
            // Défis : un point de notification quand une récompense attend,
            // plutôt qu'un « 0/3 » qu'on ne remarque pas.
            WorldCard(
                World("defis", tr("challengesCardName"), "📅"),
                cardModifier(),
                accentBorder = true,
                notification = claimable.takeIf { it > 0 },
                notificationDescription = tr("app.challengesReady", "n" to claimable),
                onClick = onOpenChallenges,
            )
            WorldCard(
                WORLD_PLAGE,
                cardModifier(),
                lockHint = plageLock,
                selected = save.currentWorld == "plage" && save.plageUnlocked,
            ) { click(WORLD_PLAGE) }
            WorldCard(
                WORLD_VILLE,
                cardModifier(),
                lockHint = villeLock,
                selected = save.currentWorld == Ville.WORLD_ID && save.villeUnlocked,
            ) { click(WORLD_VILLE) }
        }
    }
}

/** Une ligne de la grille des cartes : 3 colonnes de même largeur ET de même hauteur. */
@Composable
private fun GridRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private fun RowScope.cardModifier(): Modifier = Modifier.weight(1f).fillMaxHeight()

/**
 * `.world-card` : emoji 26px, nom en gras, bordure dorée si sélectionnée
 * (ou carte Succès/Défis), avec un `.achv-badge` en haut à droite quand il
 * y a un compteur.
 *
 * Verrouillée ([lockHint] non nul) : estompée comme sur le site, mais avec
 * en plus un cadenas et la condition de déblocage écrite sur la carte —
 * plutôt que de ne l'apprendre qu'en la touchant.
 */
@Composable
private fun WorldCard(
    world: World,
    modifier: Modifier,
    lockHint: String? = null,
    selected: Boolean = false,
    accentBorder: Boolean = false,
    badge: String? = null,
    notification: Int? = null,
    notificationDescription: String = "",
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val locked = lockHint != null
    Box(modifier = modifier.pressScale(interaction)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(PanelBg)
                .border(2.dp, if (selected || accentBorder) Accent else PanelBorder, shape)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Seuls l'emoji et le nom sont estompés : la condition de
            // déblocage, elle, doit rester parfaitement lisible.
            Column(
                modifier = Modifier.alpha(if (locked) 0.55f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(world.emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                FitText(
                    if (world.name.startsWith("app.")) tr(world.name) else world.name,
                    color = TextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (lockHint != null) {
                Text(
                    lockHint,
                    color = TextDim,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (locked) {
            Text("🔒", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        // .achv-badge : top:-8px; right:-6px — en 12sp plutôt que 10px.
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-9).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent)
                    .border(2.dp, ButtonAccentShadow, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(badge, color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (notification != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-9).dp)
                    .sizeIn(minWidth = 24.dp, minHeight = 24.dp)
                    .clip(CircleShape)
                    .background(NotificationRed)
                    .border(2.dp, Color.White, CircleShape)
                    .semantics { contentDescription = notificationDescription }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(notification.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Pastille de notification des Défis : blanc dessus à 5:1 de contraste. */
private val NotificationRed = Color(0xFFD32F2F)

/**
 * Les boutons secondaires du menu (Boutique, Classement, Profil) en tuiles
 * d'une grille de 3 colonnes : l'emoji du libellé du site au-dessus, le
 * texte en dessous, rétréci s'il ne tient pas (anglais, grande police).
 */
@Composable
private fun MenuTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    val emoji = label.substringBefore(' ', missingDelimiterValue = "")
    val text = if (emoji.isEmpty()) label else label.substringAfter(' ')
    HardShadowButton(
        modifier = modifier.heightIn(min = 48.dp),
        secondary = true,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
        onClick = onClick,
    ) { content ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (emoji.isNotEmpty()) Text(emoji, fontSize = 22.sp)
            FitText(text, color = content, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

/** [name] est soit une clé de traduction (`app.world...`), soit un texte déjà traduit. */
private data class World(val id: String, val name: String, val emoji: String)

/** Portage du tableau `WORLDS` côté web, dans le même ordre. */
private val WORLD_COUR = World("cour", "app.worldCour", "🏫")
private val WORLD_VOLCANS = World("volcans", "app.worldVolcans", "🌋")
private val WORLD_PLAGE = World("plage", "app.worldPlage", "🏖️")
private val WORLD_VILLE = World("ville", "app.worldVille", "🏙️")
