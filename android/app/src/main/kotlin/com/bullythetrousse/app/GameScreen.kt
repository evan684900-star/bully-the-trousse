@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
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
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.BumpMode
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.PhysicsConstants
import com.bullythetrousse.core.PowerAndAccuracy
import com.bullythetrousse.core.Skid
import com.bullythetrousse.core.SkinEarnings
import com.bullythetrousse.core.SkinEarningsResult
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.ThrowSequence
import com.bullythetrousse.core.ThrowState
import java.time.LocalDate

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

    fun tap() {
        // Trousse Claude : fenêtre du lancer parfait élargie (isClaude côté web).
        val equippedSkin = Skins.find(save.equippedSkin)
        val perfectWindow =
            if (equippedSkin.isClaude) PhysicsConstants.PERFECT_WINDOW_CLAUDE else PhysicsConstants.PERFECT_WINDOW
        // Trousse à Baskets : chance de rebondir au lieu de conclure le lancer.
        val bounceChances = if (equippedSkin.isBasket) Skins.BASKET_BOUNCE_CHANCES else emptyList()
        state = sequence.tap(
            SkinStats.totalPuissance(save),
            SkinStats.totalVitesse(save),
            perfectWindow,
            bounceChances,
        )
    }

    val current = state
    var landedDistance by remember { mutableStateOf(0.0) }
    var coinMultiplier by remember { mutableStateOf<Double?>(null) }
    var coinJackpot by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Le tap se prend sur tout l'écran, sans effet d'ondulation (le web
            // n'en a pas) : d'où interactionSource + indication nulle.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (current !is ThrowState.Landed) tap() },
            ),
    ) {
        // Décor + trousse : le canvas dessine son propre ciel, comme le web.
        val displayedFlight = ThrowFlight(
            state = current,
            save = save,
            onSaveChange = onSaveChange,
            onDistance = { landedDistance = it },
            onEarnings = { earnings ->
                // Bonus de la Trousse Pièce : la popup ne s'affiche que quand
                // le multiplicateur s'est vraiment déclenché (voir showCoinPopup).
                coinMultiplier = earnings.coinMultiplier
                coinJackpot = earnings.hasJackpot
            },
        )
        ThrowCanvas(
            flightState = displayedFlight,
            world = save.currentWorld,
            modifier = Modifier.fillMaxSize(),
        )
        SkyAnimation(heightFraction = 0.68f) // #screen-game .sky-anim { bottom: 32% }

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
                HintText(hintFor(current))
                Meter(current)
            }

            if (current is ThrowState.Landed) {
                ResultPanel(
                    save = save,
                    distanceMeters = current.result.distanceMeters,
                    isPerfect = current.result.isPerfect,
                    onThrowAgain = { tap() },
                    onOpenShop = onOpenShop,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        CoinPopup(
            multiplier = coinMultiplier,
            jackpot = coinJackpot,
            onDismiss = { coinMultiplier = null },
        )
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
    save: GameSave,
    distanceMeters: Double,
    isPerfect: Boolean,
    onThrowAgain: () -> Unit,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val record = if (save.currentWorld == "plage") save.plageBestDistance else save.bestDistance
    Column(
        modifier = modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (distanceMeters >= record) {
            Text("🏆 NOUVEAU RECORD !", color = Accent2, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        if (isPerfect) {
            Text("✨ LANCER PARFAIT !", color = Money, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Text(
            "Distance parcourue",
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
        FlowRowCentered(gap = 10.dp, modifier = Modifier.padding(top = 12.dp)) {
            GameButton("🔁 Relancer", small = true, onClick = onThrowAgain)
            GameButton("🛒 Boutique", secondary = true, small = true, onClick = onOpenShop)
        }
    }
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
): FlightState? {
    if (state !is ThrowState.Landed) {
        // Après un rebond (Trousse à Baskets), la trousse reste à sa position
        // cumulée plutôt que de revenir à l'origine (voir tryBasketBounce()).
        if (state is ThrowState.ChargingPower && state.bounceCount > 0) {
            return FlightState(
                worldX = state.cumulativeDistanceMeters * PhysicsConstants.SCALE,
                worldY = 0.0,
                vx = 0.0,
                vy = 0.0,
            )
        }
        return null
    }

    val result = state.result
    val isBeach = save.currentWorld == "plage"
    var flightFinished by remember(result) { mutableStateOf(false) }
    var beachOutcome by remember(result) { mutableStateOf<BeachFlightOutcome?>(null) }

    val flightState: FlightState = if (isBeach) {
        animateBeachFlight(result) { _, outcome ->
            beachOutcome = outcome
            flightFinished = true
        }
    } else {
        animateFlight(result) { flightFinished = true }
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
    LaunchedEffect(resolved, result) {
        if (!resolved) return@LaunchedEffect
        val equippedSkin = Skins.find(save.equippedSkin)
        val totalPuissance = SkinStats.totalPuissance(save)
        val totalVitesse = SkinStats.totalVitesse(save)
        val baseEarn = Economy.moneyEarned(result.distanceMeters, result.isPerfect, totalPuissance + totalVitesse)
        val earnings: SkinEarningsResult = SkinEarnings.apply(baseEarn, equippedSkin, result.distanceMeters)
        onEarnings(earnings)

        // Record par monde : le monde normal et la plage ont chacun le leur.
        var updated = if (isBeach) {
            save.copy(
                plageBestDistance = maxOf(save.plageBestDistance, result.distanceMeters),
                plageThrows = save.plageThrows + 1,
                plageMoneyEarned = save.plageMoneyEarned + earnings.finalEarn,
            )
        } else {
            save.copy(bestDistance = maxOf(save.bestDistance, result.distanceMeters))
        }
        updated = updated.copy(
            money = updated.money + earnings.finalEarn,
            totalMoneyEarned = updated.totalMoneyEarned + earnings.finalEarn,
            totalThrows = updated.totalThrows + 1,
            hasJackpot = updated.hasJackpot || earnings.hasJackpot,
        )
        if (isSkidding) updated = Skid.applyDurabilityCost(updated)
        beachOutcome?.let { outcome ->
            if (outcome.parasolBounced) updated = updated.copy(plageParasolBounces = updated.plageParasolBounces + 1)
            if (outcome.towelFound) updated = updated.copy(plageTowelsFound = updated.plageTowelsFound + 1)
            if (outcome.castleCrushed) updated = updated.copy(plageCastlesCrushed = updated.plageCastlesCrushed + 1)
        }

        // Défis quotidiens (voir bumpDailyChallenge() côté web).
        updated = DailyChallenges.ensure(updated, LocalDate.now().toString(), totalPuissance, totalVitesse)
        updated = DailyChallenges.bump(updated, "throws", 1.0, BumpMode.ADD)
        updated = DailyChallenges.bump(updated, "distance", result.distanceMeters, BumpMode.MAX)
        updated = DailyChallenges.bump(updated, "distanceCumul", result.distanceMeters, BumpMode.ADD)
        updated = DailyChallenges.bump(updated, "earn", earnings.finalEarn.toDouble(), BumpMode.ADD)
        if (result.isPerfect) updated = DailyChallenges.bump(updated, "perfect", 1.0, BumpMode.ADD)
        if (isSkidding) updated = DailyChallenges.bump(updated, "skid", 1.0, BumpMode.ADD)
        if (result.isPerfect) updated = updated.copy(hasPerfectThrow = true)

        onSaveChange(Achievements.apply(updated))
    }

    return displayed
}
