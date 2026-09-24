@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.BumpMode
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.Milestones
import com.bullythetrousse.core.LunarBonus
import com.bullythetrousse.core.DailyStats
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.HapticEvent
import com.bullythetrousse.core.PhysicsConstants
import com.bullythetrousse.core.PowerAndAccuracy
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.Skid
import com.bullythetrousse.core.SkinEarnings
import com.bullythetrousse.core.SkinEarningsResult
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.SpacePhase
import com.bullythetrousse.core.SpaceSequence
import com.bullythetrousse.core.SpaceState
import com.bullythetrousse.core.ThrowSequence
import com.bullythetrousse.core.ThrowState
import java.time.LocalDate
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.bullythetrousse.core.CourDecor

/**
 * Écran de jeu, porté de `#screen-game` (index.html) : le canvas en plein
 * écran, `.sky-anim` par-dessus, le bouton `.back-btn`, le `.hud-top`
 * (argent + distance), la consigne `.hint-text`, la jauge `#meter-wrap`
 * (remplissage pour la puissance, curseur + repère central pour la
 * précision) et le `.result-panel` à l'atterrissage.
 *
 * Toute la surface est cliquable : côté web, `handleTap()` est branché sur
 * l'écran entier, pas sur un bouton.
 */
@Composable
fun GameScreen(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onBackToMenu: () -> Unit,
    onOpenShop: () -> Unit,
) {
    val sequence = remember { ThrowSequence() }
    var state by remember { mutableStateOf<ThrowState>(sequence.state) }
    val sfx = LocalSfx.current
    val haptics = LocalHaptics.current

    fun tap() {
        // Trousse Claude : fenêtre du lancer parfait élargie (isClaude côté web).
        val equippedSkin = Skins.find(save.equippedSkin)
        val perfectWindow =
            if (equippedSkin.isClaude) PhysicsConstants.PERFECT_WINDOW_CLAUDE else PhysicsConstants.PERFECT_WINDOW
        // Trousse à Baskets : chance de rebondir au lieu de conclure le lancer.
        val bounceChances = if (equippedSkin.isBasket) Skins.BASKET_BOUNCE_CHANCES else emptyList()
        val before = state
        state = sequence.tap(
            SkinStats.totalPuissance(save),
            SkinStats.totalVitesse(save),
            perfectWindow,
            bounceChances,
        )
        // Mêmes points d'appel que le site : sfxCharge() dans lockPower(),
        // sfxLaunch() (+ sfxCoinFlip() pour la Trousse Pièce) dans
        // lockAccuracyAndLaunch().
        when {
            before !is ThrowState.ChargingAccuracy && state is ThrowState.ChargingAccuracy -> {
                sfx.play(SfxCatalog.CHARGE)
                haptics.play(HapticEvent.CHARGE)
            }
            before is ThrowState.ChargingAccuracy && state is ThrowState.Landed -> {
                sfx.play(SfxCatalog.LAUNCH)
                if (equippedSkin.isCoin) sfx.play(SfxCatalog.COIN_FLIP)
                haptics.play(HapticEvent.LAUNCH)
            }
        }
    }

    val current = state
    var landedDistance by remember { mutableStateOf(0.0) }
    var coinMultiplier by remember { mutableStateOf<Double?>(null) }
    var coinJackpot by remember { mutableStateOf(false) }
    // Toast de l'écran de jeu (palier atteint...), comme `showToast()` côté site.
    var toast by remember { mutableStateOf<String?>(null) }
    // Le détour en apesanteur du lancer en cours, tant qu'il dure : c'est lui
    // qui reçoit les taps sur les anneaux du QTE.
    var spaceFlight by remember { mutableStateOf<SpaceFlight?>(null) }
    // Le boost 🦇 du lancer en cours, quand la Trousse Vampire est équipée.
    var vampireBoost by remember { mutableStateOf<VampireBoostController?>(null) }

    // sfxSpace() : joué une fois, au basculement en apesanteur.
    val spacePhase = spaceFlight?.state?.phase
    LaunchedEffect(spacePhase) {
        if (spacePhase == SpacePhase.FLOATING) {
            sfx.play(SfxCatalog.SPACE)
            haptics.play(HapticEvent.RECORD)
        }
    }

    // Easter egg 💩 : visible dans la cour tant que la trousse n'est pas
    // partie (poopEggVisible() côté site). La position du dernier appui est
    // retenue pour savoir si le tap est tombé dessus.
    val poopVisible = save.currentWorld == "cour" && current !is ThrowState.Landed &&
        !(current is ThrowState.ChargingPower && current.bounceCount > 0)
    val lastDown = remember { floatArrayOf(-1f, -1f) }
    val lastDownHeight = remember { floatArrayOf(0f) }
    val poopHitRadius = with(LocalDensity.current) { maxOf(CourDecor.POOP_EGG_HIT_RADIUS.toFloat(), 16.dp.toPx()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { lastDownHeight[0] = it.height.toFloat() }
            // Observe l'appui sans le consommer : le tap reste géré plus bas.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    lastDown[0] = down.position.x
                    lastDown[1] = down.position.y
                }
            }
            // Le tap se prend sur tout l'écran, sans effet d'ondulation (le web
            // n'en a pas) : d'où interactionSource + indication nulle.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // Un tap sur le 💩 est « absorbé » : il ne lance pas la charge,
                    // le temps de voir la découverte (revealPoopEgg()).
                    if (poopVisible && CourDecor.hitsPoopEgg(
                            tapX = lastDown[0].toDouble(),
                            tapY = lastDown[1].toDouble(),
                            cameraX = 0.0,
                            groundScreenY = (lastDownHeight[0] * GROUND_FRACTION).toDouble(),
                            hitRadius = poopHitRadius.toDouble(),
                        )
                    ) {
                        toast = "💩"
                        if (!save.hasFoundPoopEgg) onSaveChange(save.copy(hasFoundPoopEgg = true))
                        return@clickable
                    }
                    // En apesanteur, le tap sert au QTE — pas à relancer.
                    val space = spaceFlight
                    if (space?.state != null) {
                        val ringsBefore = space.state?.ringResults?.size ?: 0
                        space.tap()
                        // advanceQteRing(hit) : sfxCharge() si touché, sinon sfxError().
                        val after = space.state?.ringResults
                        if (after != null && after.size > ringsBefore) {
                            val hit = after.last()
                            sfx.play(if (hit) SfxCatalog.CHARGE else SfxCatalog.ERROR)
                            haptics.play(if (hit) HapticEvent.QTE_HIT else HapticEvent.ERROR)
                        }
                    } else if (current !is ThrowState.Landed) {
                        tap()
                    }
                },
            ),
    ) {
        // Décor + trousse : le canvas dessine son propre ciel, comme le web.
        val flight = ThrowFlight(
            state = current,
            save = save,
            onSaveChange = onSaveChange,
            onDistance = { landedDistance = it },
            onEarnings = { earnings ->
                // Bonus de la Trousse Pièce : la popup ne s'affiche que quand
                // le multiplicateur s'est vraiment déclenché (voir showCoinPopup).
                coinMultiplier = earnings.coinMultiplier
                coinJackpot = earnings.hasJackpot
                if (earnings.coinMultiplier != null) {
                    sfx.play(SfxCatalog.COIN_BONUS)
                    haptics.play(HapticEvent.RECORD)
                }
            },
            onSpaceFlight = { spaceFlight = it },
            onVampireBoost = { vampireBoost = it },
            onToast = { toast = it },
        )
        ThrowCanvas(
            flightState = flight.state,
            world = save.currentWorld,
            equippedSkin = save.equippedSkin,
            equippedTrail = save.equippedTrail,
            spaceState = spaceFlight?.state,
            showPoopEgg = poopVisible,
            groundVerticalFraction = GROUND_FRACTION,
            modifier = Modifier.fillMaxSize(),
        )
        // #screen-game .sky-anim { bottom: 32% } — jour/nuit selon save.theme
        SkyAnimation(heightFraction = 0.68f, night = save.theme != "light", world = save.currentWorld)

        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // .back-btn : coin haut-gauche, 10px de marge.
            GameButton(
                "← Menu",
                secondary = true,
                small = true,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                onClick = onBackToMenu,
            )

            // .hud-top : sous le bouton retour, argent à gauche, distance à droite.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MoneyPill("💰 ${save.money} $", small = true)
                MoneyPill("📏 ${"%.0f".format(landedDistance)} m", small = true, color = TextColor)
            }

            // .hint-text (bottom: 90px) et #meter-wrap (bottom: 70px)
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HintText(spaceFlight?.state?.let { hintForSpace(it) } ?: hintFor(current))
                Meter(current)
            }

            // Le panneau n'apparaît qu'une fois la trousse VRAIMENT immobilisée :
            // `ThrowState.Landed` arrive dès le tap de visée (le résultat est
            // calculé d'un coup côté `:core`), le vol n'est qu'une animation
            // jouée ensuite. Annoncer la distance pendant que la trousse est
            // encore en l'air spoilerait le lancer.
            val throwSummary = flight.summary
            if (current is ThrowState.Landed && flight.resolved && throwSummary != null) {
                ResultPanel(
                    summary = throwSummary,
                    distanceMeters = current.result.distanceMeters,
                    isPerfect = current.result.isPerfect,
                    onThrowAgain = { tap() },
                    onOpenShop = onOpenShop,
                    modifier = Modifier.align(Alignment.Center),
                )
            }


            // #btn-vampire-boost : rond violet en bas au centre, visible
            // uniquement pendant le vol d'une Trousse Vampire dont le budget
            // de boost n'est pas épuisé.
            vampireBoost?.let { boost ->
                if (boost.isVisible(flying = flight.state != null && !flight.resolved)) {
                    VampireBoostButton(
                        boost = boost,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                    )
                }
            }
        }

        // #screen-game.vampire-boosting::after : vignette violette pulsante
        // tant que le boost est activement maintenu.
        VampireBoostVignette(active = vampireBoost?.state?.boosting == true)

        CoinPopup(
            multiplier = coinMultiplier,
            jackpot = coinJackpot,
            onDismiss = { coinMultiplier = null },
        )
        Toast(message = toast, onDismiss = { toast = null })
    }
}

/** `#hint-text` : la consigne change selon l'étape du lancer. */
private fun hintFor(state: ThrowState): String = when (state) {
    is ThrowState.Idle -> "Clique / appuie pour charger la puissance !"
    is ThrowState.ChargingPower ->
        if (state.bounceCount > 0) {
            "🏀 Rebond ! (${"%.1f".format(state.cumulativeDistanceMeters)} m) Appuie à nouveau !"
        } else {
            "Appuie pour figer la puissance !"
        }
    is ThrowState.ChargingAccuracy -> "Appuie pour lancer !"
    is ThrowState.Landed -> ""
}

/** `hintZeroG` côté site, plus le numéro de l'anneau en cours : pendant le
 *  QTE, le joueur doit savoir où il en est dans la série. */
private fun hintForSpace(space: SpaceState): String = when (space.phase) {
    SpacePhase.TRANSITION, SpacePhase.DONE -> ""
    SpacePhase.FLOATING -> "🛸 En apesanteur..."
    SpacePhase.QTE -> "🎯 Tape quand les anneaux se superposent ! (${space.ringResults.size + 1})"
}

/**
 * Ce que [ThrowFlight] rend à l'écran de jeu : la position à dessiner
 * (`null` tant que rien n'est en l'air) et si le lancer est complètement
 * terminé — vol fini, dérapage éventuel compris.
 */
private data class FlightDisplay(
    val state: FlightState?,
    val resolved: Boolean,
    val summary: ThrowSummary? = null,
)

/**
 * Ce que le panneau de résultat affiche en plus de la distance, calculé une
 * fois le lancer résolu — les lignes `#result-*` de `onLanded()` côté site.
 */
private data class ThrowSummary(
    val isRecord: Boolean,
    val earn: Int,
    val coinMultiplier: Double?,
    val coinExtra: Int,
    val vampireStolen: Int,
    val vampireStealPct: Double,
    val skidded: Boolean,
    val beach: BeachFlightOutcome?,
)

/**
 * `#meter-wrap` : barre de 26px de haut, très arrondie, fond sombre. Pendant
 * la charge de puissance elle se remplit (`#meter-fill`, dégradé vert → doré
 * → rouge) ; pendant la visée, le repère central (`#meter-center`) et le
 * curseur qui va-et-vient (`#meter-marker`) s'affichent à la place.
 */
@Composable
private fun Meter(state: ThrowState) {
    val startedAt = when (state) {
        is ThrowState.ChargingPower -> state.startedAtMillis
        is ThrowState.ChargingAccuracy -> state.startedAtMillis
        else -> return
    }
    val isPower = state is ThrowState.ChargingPower

    // Même base de temps que ThrowSequence (System.currentTimeMillis), pour que
    // la valeur affichée soit exactement celle qui serait figée en tapant.
    val live = remember(startedAt) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(startedAt, isPower) {
        while (true) {
            withFrameNanos { }
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
            live.doubleValue =
                if (isPower) PowerAndAccuracy.powerFraction(elapsed) else PowerAndAccuracy.accuracyValue(elapsed)
        }
    }

    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(0.8f) // width: min(80vw, 420px)
            .height(26.dp)
            .clip(shape)
            .background(Color(0x8C0A0C14)) // rgba(10,12,20,0.55)
            .border(2.dp, Color(0xFF222633), shape),
    ) {
        if (isPower) {
            // #meter-fill : dégradé vert → doré → rouge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(live.doubleValue.toFloat().coerceIn(0f, 1f))
                    .background(Brush.horizontalGradient(listOf(Money, Accent, Accent2))),
            )
        } else {
            // #meter-center : repère blanc semi-transparent au milieu
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.35f)),
            )
            // #meter-marker : curseur blanc, accuracyValue va de -1 à +1
            val marker = ((live.doubleValue + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth(marker)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

/**
 * `.result-panel` : panneau centré affiché à l'atterrissage — record et
 * lancer parfait le cas échéant, distance en gros, gain en vert, puis les
 * deux boutons "Relancer" / "Boutique".
 */
@Composable
private fun ResultPanel(
    summary: ThrowSummary,
    distanceMeters: Double,
    isPerfect: Boolean,
    onThrowAgain: () -> Unit,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (summary.isRecord) {
            Text(tr("resultRecord"), color = Accent2, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        if (isPerfect) {
            Text(tr("resultPerfect"), color = Money, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Text(
            tr("resultDistanceTitle"),
            color = Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            "${"%.1f".format(distanceMeters)} m",
            color = TextColor,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
        )
        // .earn
        Text("+${summary.earn} $", color = Money, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        // #result-coin / #result-vampire : textes en dur côté site (pas dans STRINGS).
        summary.coinMultiplier?.let { m ->
            ResultNote(
                "🪙 Multiplicateur x${formatMultiplier(m)} ! (+${summary.coinExtra}$ grâce au bonus)",
                Accent,
            )
        }
        if (summary.vampireStolen > 0 || summary.vampireStealPct > 0) {
            ResultNote(
                "🦇 -${summary.vampireStolen}$ volés par la malédiction (${summary.vampireStealPct.roundToInt()}%)",
                Color(0xFFB388FF),
            )
        }
        // #result-skid
        if (summary.skidded) ResultNote(tr("skidWarning"), Color(0xFFFF6B6B))
        // #result-beach : une ligne par évènement de la plage, comme beachResultText().
        summary.beach?.let { beach ->
            val lines = buildList {
                if (beach.parasolBounced) add(tr("beachResultParasol"))
                if (beach.towelFound) add(tr("beachResultTowel"))
                if (beach.castleCrushed) add(tr("beachResultCastle"))
            }
            if (lines.isNotEmpty()) ResultNote(lines.joinToString("\n"), Color(0xFF6BFFB0))
        }
        FlowRowCentered(gap = 10.dp, modifier = Modifier.padding(top = 12.dp)) {
            GameButton(tr("btnThrowAgain"), small = true, onClick = onThrowAgain)
            GameButton(tr("btnShop"), secondary = true, small = true, onClick = onOpenShop)
        }
    }
}

/** Les petites lignes colorées sous le gain (`#result-coin`, `#result-skid`...). */
@Composable
private fun ResultNote(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
        modifier = Modifier.widthIn(max = 280.dp).padding(top = 6.dp),
    )
}

/**
 * Anime le lancer en cours et applique ses conséquences (gains, record,
 * défis, succès) une seule fois, quand tout est résolu — portage de
 * `onLanded()`/`persist()` côté web. Renvoie l'état de vol à dessiner,
 * ou `null` tant qu'aucun lancer n'est en l'air.
 */
@Composable
private fun ThrowFlight(
    state: ThrowState,
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onDistance: (Double) -> Unit,
    onEarnings: (SkinEarningsResult) -> Unit,
    onSpaceFlight: (SpaceFlight?) -> Unit,
    onVampireBoost: (VampireBoostController?) -> Unit,
    onToast: (String) -> Unit,
): FlightDisplay {
    if (state !is ThrowState.Landed) {
        // Après un rebond (Trousse à Baskets), la trousse reste à sa position
        // cumulée plutôt que de revenir à l'origine (voir tryBasketBounce()).
        if (state is ThrowState.ChargingPower && state.bounceCount > 0) {
            return FlightDisplay(
                state = FlightState(
                    worldX = state.cumulativeDistanceMeters * PhysicsConstants.SCALE,
                    worldY = 0.0,
                    vx = 0.0,
                    vy = 0.0,
                ),
                resolved = false,
            )
        }
        return FlightDisplay(state = null, resolved = false)
    }

    val sfx = LocalSfx.current
    val haptics = LocalHaptics.current
    val result = state.result
    val isBeach = save.currentWorld == "plage"
    var flightFinished by remember(result) { mutableStateOf(false) }
    var beachOutcome by remember(result) { mutableStateOf<BeachFlightOutcome?>(null) }

    // Easter egg « vers l'espace » : puissance au maximum ET visée tout au
    // début de la barre (voir SpaceSequence.shouldTrigger, `:core`). Le
    // Boeing 747 en est exclu, c'est le prix de sa vitesse au sol.
    val equipped = Skins.find(save.equippedSkin)
    val goesToSpace = remember(result) {
        SpaceSequence.shouldTrigger(result.lockedPower, result.accuracyValue, equipped)
    }
    var spaceOutcome by remember(result) { mutableStateOf<SpaceState?>(null) }
    val spaceFlight = rememberSpaceFlight(result, equipped) { outcome ->
        spaceOutcome = outcome
        // sfxRecord() pour le combo parfait (tous les anneaux touchés).
        if (SpaceSequence.isPerfect(outcome, equipped)) {
            sfx.play(SfxCatalog.RECORD)
            haptics.play(HapticEvent.RECORD)
        }
    }
    // L'écran de jeu a besoin du pilote pour lui router les taps du QTE et
    // dessiner les anneaux ; il ne le reçoit que si ce lancer part vraiment.
    LaunchedEffect(result, goesToSpace) {
        onSpaceFlight(if (goesToSpace) spaceFlight else null)
    }

    // Boost 🦇 : réarmé à chaque lancer (une utilisation par lancer). L'écran
    // n'en reçoit un que tant que la trousse est en l'air — le bouton doit
    // disparaître dès l'atterrissage, comme la condition `state === "flying"`
    // du site.
    val vampireBoost = rememberVampireBoost(result, equipped)
    LaunchedEffect(result, flightFinished) {
        onVampireBoost(if (flightFinished) null else vampireBoost)
    }

    val flightState: FlightState = if (isBeach) {
        animateBeachFlight(result, vampire = vampireBoost) { _, outcome ->
            beachOutcome = outcome
            flightFinished = true
            sfx.play(SfxCatalog.LAND)
            haptics.play(HapticEvent.LAND)
        }
    } else {
        animateFlight(
            result,
            space = if (goesToSpace) spaceFlight else null,
            vampire = vampireBoost,
        ) {
            flightFinished = true
            // `inSpaceMode` côté site : un lancer revenu de l'espace s'écrase
            // au lieu d'atterrir.
            if (goesToSpace) {
                sfx.play(SfxCatalog.CRASH)
                haptics.play(HapticEvent.CRASH)
            } else {
                sfx.play(SfxCatalog.LAND)
                haptics.play(HapticEvent.LAND)
            }
        }
    }

    // Monde Volcan : la poussière rouge rend le sol glissant (voir Skid).
    var isSkidding by remember(result) { mutableStateOf(false) }
    var skidDecided by remember(result) { mutableStateOf(false) }
    var skidFinished by remember(result) { mutableStateOf(false) }
    LaunchedEffect(flightFinished) {
        if (flightFinished && !skidDecided) {
            skidDecided = true
            isSkidding = save.currentWorld == "volcans" && Skid.shouldSkid()
        }
    }

    val displayed = if (isSkidding && !skidFinished) {
        animateSkid(landingWorldX = flightState.worldX) { skidFinished = true }
    } else {
        flightState
    }

    LaunchedEffect(displayed.worldX) {
        onDistance(displayed.worldX / PhysicsConstants.SCALE)
    }

    val resolved = flightFinished && skidDecided && (!isSkidding || skidFinished)
    var summary by remember(result) { mutableStateOf<ThrowSummary?>(null) }
    LaunchedEffect(resolved, result) {
        if (!resolved) return@LaunchedEffect
        val today = LocalDate.now()
        val equippedSkin = Skins.find(save.equippedSkin)
        val totalPuissance = SkinStats.totalPuissance(save)
        val totalVitesse = SkinStats.totalVitesse(save)
        var updated = save

        // Dérapage (monde Volcan) : -10 de durabilité et message rouge.
        if (isSkidding) updated = Skid.applyDurabilityCost(updated)

        // Record par monde : le monde normal et la plage ont chacun le leur.
        val previousRecord = if (isBeach) save.plageBestDistance else save.bestDistance
        val isRecord = result.distanceMeters > previousRecord
        if (isRecord) {
            sfx.play(SfxCatalog.RECORD)
            haptics.play(HapticEvent.RECORD)
            updated = if (isBeach) {
                updated.copy(plageBestDistance = result.distanceMeters)
            } else {
                updated.copy(bestDistance = result.distanceMeters)
            }
        }

        // Gain : base + parfait + améliorations, puis le bonus lunaire (après
        // l'espace seulement), puis la pièce, puis le tribut vampire — dans
        // cet ordre précis, comme côté site.
        val base = Economy.moneyEarned(result.distanceMeters, result.isPerfect, totalPuissance + totalVitesse)
        val withLunar = LunarBonus.apply(base, equippedSkin, cameFromSpace = goesToSpace)
        val earnings: SkinEarningsResult = SkinEarnings.apply(withLunar, equippedSkin, result.distanceMeters)
        onEarnings(earnings)
        val earn = earnings.finalEarn
        // Le « +X $ grâce au bonus » de la pièce, calculé avant le tribut
        // vampire (les deux ne peuvent de toute façon pas se cumuler).
        val coinExtra = earnings.coinMultiplier?.let { (withLunar * it).roundToInt() - withLunar } ?: 0

        updated = updated.copy(
            money = updated.money + earn,
            hasJackpot = updated.hasJackpot || earnings.hasJackpot,
        )
        updated = DailyStats.recordEarning(updated, earn, today)
        updated = DailyStats.recordDistance(updated, result.distanceMeters, today)
        var totalGained = earn

        // Palier de 100 m : une seule fois par palier, même franchis en rafale.
        Milestones.reward(updated, result.distanceMeters)?.let { reward ->
            updated = updated.copy(
                milestoneReached = reward.milestone,
                money = updated.money + reward.bonus,
            )
            updated = DailyStats.recordEarning(updated, reward.bonus, today)
            totalGained += reward.bonus
            onToast("🎉 Palier des ${reward.meters}m atteint ! +${reward.bonus}$")
        }

        updated = updated.copy(totalThrows = updated.totalThrows + 1)
        if (isBeach) updated = updated.copy(plageThrows = updated.plageThrows + 1)
        beachOutcome?.let { outcome ->
            if (outcome.parasolBounced) updated = updated.copy(plageParasolBounces = updated.plageParasolBounces + 1)
            if (outcome.towelFound) updated = updated.copy(plageTowelsFound = updated.plageTowelsFound + 1)
            if (outcome.castleCrushed) updated = updated.copy(plageCastlesCrushed = updated.plageCastlesCrushed + 1)
        }

        // Défis quotidiens (voir bumpDailyChallenge() côté web) : "volcan" et
        // "skid" seulement au monde Volcan hors apesanteur.
        updated = DailyChallenges.ensure(updated, today.toString(), totalPuissance, totalVitesse)
        updated = DailyChallenges.bump(updated, "throws", 1.0, BumpMode.ADD)
        updated = DailyChallenges.bump(updated, "distance", result.distanceMeters, BumpMode.MAX)
        updated = DailyChallenges.bump(updated, "distanceCumul", result.distanceMeters, BumpMode.ADD)
        updated = DailyChallenges.bump(updated, "earn", totalGained.toDouble(), BumpMode.ADD)
        if (result.isPerfect) updated = DailyChallenges.bump(updated, "perfect", 1.0, BumpMode.ADD)
        if (isSkidding) updated = DailyChallenges.bump(updated, "skid", 1.0, BumpMode.ADD)
        if (save.currentWorld == "volcans" && !goesToSpace) {
            updated = DailyChallenges.bump(updated, "volcan", 1.0, BumpMode.ADD)
        }
        if (result.isPerfect) updated = updated.copy(hasPerfectThrow = true)

        // Passage par l'apesanteur : le succès de l'easter egg, et celui des
        // réflexes parfaits (+ le défi "qte") si tous les anneaux sont réussis.
        spaceOutcome?.let { outcome ->
            updated = updated.copy(hasTriggeredSpaceEgg = true)
            if (SpaceSequence.isPerfect(outcome, equipped)) {
                updated = updated.copy(hasPerfectQte = true)
                updated = DailyChallenges.bump(updated, "qte", 1.0, BumpMode.ADD)
            }
        }

        summary = ThrowSummary(
            isRecord = isRecord,
            earn = earn,
            coinMultiplier = earnings.coinMultiplier,
            coinExtra = coinExtra,
            vampireStolen = earnings.vampireStolen,
            vampireStealPct = earnings.vampireStealPct,
            skidded = isSkidding,
            beach = beachOutcome,
        )
        // Les succès sont vérifiés par updateSave (MainActivity), point de
        // passage unique : c'est lui qui annonce ceux qui tombent.
        onSaveChange(updated)
    }

    return FlightDisplay(state = displayed, resolved = resolved && summary != null, summary = summary)
}

/**
 * `#btn-vampire-boost` : rond violet de 64px en bas au centre. Maintenir
 * accélère la trousse ; relâcher met en pause sans consommer le reste du
 * budget (voir [VampireBoost], `:core`).
 *
 * La détection se fait à la main plutôt qu'avec `clickable` : il faut les
 * évènements d'appui ET de relâchement séparément, et un simple clic ne
 * permettrait pas de maintenir. `awaitEachGesture` couvre aussi l'annulation
 * (doigt qui sort du bouton), traitée comme un relâchement — sans quoi le
 * boost resterait actif indéfiniment.
 */
@Composable
private fun VampireBoostButton(boost: VampireBoostController, modifier: Modifier = Modifier) {
    val pressed = boost.state.boosting
    val haptics = LocalHaptics.current
    Box(
        modifier = modifier
            // :active { transform: scale(0.9) }
            .scale(if (pressed) 0.9f else 1f)
            .size(64.dp)
            .clip(CircleShape)
            // `radial-gradient(circle at 35% 30%, ...)` : le centre du dégradé
            // est exprimé en pourcentage côté CSS mais en pixels dans un Brush,
            // d'où le dessin à la main, qui lui connaît sa taille.
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF6A1B9A), Color(0xFF2C0A3E)),
                        center = Offset(size.width * 0.35f, size.height * 0.30f),
                        radius = size.maxDimension * 0.8f,
                    ),
                )
            }
            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .pointerInput(boost) {
                awaitEachGesture {
                    // Consommé pour que l'appui ne remonte pas au clic
                    // plein écran de l'écran de jeu, comme le
                    // `e.preventDefault()` de startVampireBoost().
                    awaitFirstDown().consume()
                    boost.press()
                    haptics.play(HapticEvent.VAMPIRE_BOOST)
                    // Renvoie null si le geste est annulé : dans les deux cas
                    // le boost doit s'arrêter.
                    waitForUpOrCancellation()
                    boost.release()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("\uD83E\uDD87", fontSize = 28.sp)
    }
}

/**
 * `#screen-game.vampire-boosting::after` : halo violet pulsant sur les bords
 * de l'écran tant que le boost est maintenu, pour que l'accélération se voie
 * ailleurs que sur le compteur de distance.
 */
@Composable
private fun VampireBoostVignette(active: Boolean) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "vampire-boost-pulse")
    // from { opacity: 0.6 } to { opacity: 1 }, 0.5s alternée.
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // `box-shadow: inset` n'a pas d'équivalent : un dégradé radial
                // transparent au centre et violet sur les bords donne le même
                // assombrissement périphérique.
                Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color(0xFFA028DC).copy(alpha = 0.55f * alpha),
                    ),
                ),
            ),
    )
}

/** Hauteur du sol à l'écran (fraction de la hauteur), partagée entre le
 *  canvas et le test du toucher sur le 💩. */
private const val GROUND_FRACTION = 0.68f
