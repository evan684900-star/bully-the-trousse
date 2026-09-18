package com.bullythetrousse.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
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
import com.bullythetrousse.core.BumpMode
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.Achievements
import java.time.LocalDate

/**
 * Écran de jeu : uniquement la trousse (Canvas + boucle de lancer en 3
 * taps). La boutique/les skins/les défis/les succès ont leur propre écran
 * (voir MenuScreen.kt/ShopScreen.kt) — avant cette tranche, tout était
 * empilé ici (voir android/README.md).
 *
 * Simplification assumée : revenir au menu en pleine charge (avant le 3e
 * tap) abandonne ce lancer plutôt que de le mettre en pause — un nouvel
 * écran Jeu repart de `Idle` (voir `remember { ThrowSequence() }`
 * ci-dessous, recréé à chaque composition de cet écran).
 */
@Composable
fun GameScreen(save: GameSave, onSaveChange: (GameSave) -> Unit, onBackToMenu: () -> Unit) {
    val sequence = remember { ThrowSequence() }
    var state by remember { mutableStateOf<ThrowState>(sequence.state) }

    fun tap() {
        // Trousse Claude : fenêtre du lancer parfait élargie de 75%, voir
        // PhysicsConstants.PERFECT_WINDOW_CLAUDE (portage de isClaude côté web).
        val equippedSkin = Skins.find(save.equippedSkin)
        val perfectWindow = if (equippedSkin.isClaude) PhysicsConstants.PERFECT_WINDOW_CLAUDE else PhysicsConstants.PERFECT_WINDOW
        // Trousse à Baskets : chance de rebondir plutôt que de conclure le
        // lancer, voir Skins.BASKET_BOUNCE_CHANCES et ThrowSequence.tap().
        val bounceChances = if (equippedSkin.isBasket) Skins.BASKET_BOUNCE_CHANCES else emptyList()
        state = sequence.tap(SkinStats.totalPuissance(save), SkinStats.totalVitesse(save), perfectWindow, bounceChances)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .clickable { tap() },
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        OutlinedButton(onClick = onBackToMenu) { Text("🏠 Menu") }

        val displayedRecord = if (save.currentWorld == "plage") save.plageBestDistance else save.bestDistance
        Text("💰 ${save.money}   Record : ${"%.1f".format(displayedRecord)} m")
        Text("Puissance ${SkinStats.totalPuissance(save)}   Vitesse ${SkinStats.totalVitesse(save)}   Durabilité ${save.durability}/${SkinStats.maxDurability(save)}")

        when (val current = state) {
            is ThrowState.Idle -> {
                ThrowCanvas(flightState = null, world = save.currentWorld)
                Text("Tape l'écran pour commencer à charger la puissance.")
            }

            is ThrowState.ChargingPower -> {
                // Après un rebond (Trousse à Baskets), la trousse reste posée à
                // sa position cumulée plutôt que de sauter à l'origine (voir
                // tryBasketBounce() côté web : worldX n'est jamais réinitialisé
                // entre deux segments, seuls vx/vy/rotation repartent de zéro).
                val restingFlightState = if (current.bounceCount > 0) {
                    FlightState(worldX = current.cumulativeDistanceMeters * PhysicsConstants.SCALE, worldY = 0.0, vx = 0.0, vy = 0.0)
                } else {
                    null
                }
                ThrowCanvas(flightState = restingFlightState, world = save.currentWorld)
                if (current.bounceCount > 0) {
                    // Portage du hint "hintBasketBounce" côté web : la trousse a
                    // rebondi (tryBasketBounce()), il faut retaper la charge.
                    Text("🏀 Rebond ! (${"%.1f".format(current.cumulativeDistanceMeters)} m déjà parcourus) Tape à nouveau !")
                } else {
                    Text("Puissance en charge... tape pour la figer.")
                }
                LiveOscillatingBar(
                    startedAtMillis = current.startedAtMillis,
                    valueAt = PowerAndAccuracy::powerFraction,
                    // powerFraction oscille dans [0.4, 1.0] : la barre reste
                    // donc toujours au moins un peu remplie.
                    toProgress = { it.toFloat() },
                )
            }

            is ThrowState.ChargingAccuracy -> {
                ThrowCanvas(flightState = null, world = save.currentWorld)
                Text("Puissance figée à ${(current.lockedPower * 100).toInt()}%.")
                Text("Précision en charge... tape pour lancer.")
                LiveOscillatingBar(
                    startedAtMillis = current.startedAtMillis,
                    valueAt = PowerAndAccuracy::accuracyValue,
                    // accuracyValue oscille dans [-1, 1], 0 = visée parfaite
                    // (le milieu de la barre) : on recentre pour la jauge.
                    toProgress = { ((it + 1.0) / 2.0).toFloat() },
                )
            }

            is ThrowState.Landed -> {
                val result = current.result
                val isBeach = save.currentWorld == "plage"
                var flightFinished by remember(result) { mutableStateOf(false) }
                var beachOutcome by remember(result) { mutableStateOf<BeachFlightOutcome?>(null) }
                val flightState: FlightState = if (isBeach) {
                    animateBeachFlight(result) { finalState, outcome ->
                        beachOutcome = outcome
                        flightFinished = true
                    }
                } else {
                    animateFlight(result) { flightFinished = true }
                }

                // Monde Volcan : la poussière rouge rend le sol glissant (25% de
                // chance), voir Skid dans :core (portage de SKID_CHANCE et du
                // bloc "skidding" de gameLoop()). Décidé une seule fois par
                // lancer, dès que le vol se termine.
                var isSkidding by remember(result) { mutableStateOf(false) }
                var skidDecided by remember(result) { mutableStateOf(false) }
                var skidFinished by remember(result) { mutableStateOf(false) }
                LaunchedEffect(flightFinished) {
                    if (flightFinished && !skidDecided) {
                        skidDecided = true
                        isSkidding = save.currentWorld == "volcans" && Skid.shouldSkid()
                    }
                }

                val displayedFlightState = if (isSkidding && !skidFinished) {
                    animateSkid(landingWorldX = flightState.worldX) { skidFinished = true }
                } else {
                    flightState
                }
                ThrowCanvas(flightState = displayedFlightState, world = save.currentWorld)

                val throwResolved = flightFinished && skidDecided && (!isSkidding || skidFinished)
                var earnings by remember(result) { mutableStateOf<SkinEarningsResult?>(null) }

                // Porté de onLanded()/persist() côté web : argent gagné (avec les
                // bonus/malus de skin — Trousse Pièce, Trousse Vampire, voir
                // SkinEarnings), record, nombre de lancers et coût en durabilité
                // d'un dérapage éventuel, mis à jour puis sauvegardés une seule
                // fois par lancer, une fois le vol ET un éventuel dérapage
                // entièrement résolus.
                LaunchedEffect(throwResolved, result) {
                    if (!throwResolved) return@LaunchedEffect
                    val equippedSkin = Skins.find(save.equippedSkin)
                    val totalPuissance = SkinStats.totalPuissance(save)
                    val totalVitesse = SkinStats.totalVitesse(save)
                    val baseEarn = Economy.moneyEarned(result.distanceMeters, result.isPerfect, totalPuissance + totalVitesse)
                    val skinEarnings = SkinEarnings.apply(baseEarn, equippedSkin, result.distanceMeters)
                    earnings = skinEarnings
                    // Record par monde : le monde normal et la plage ont chacun
                    // le leur (voir le commentaire de recordField côté web).
                    var updated = if (isBeach) {
                        save.copy(
                            plageBestDistance = maxOf(save.plageBestDistance, result.distanceMeters),
                            plageThrows = save.plageThrows + 1,
                            plageMoneyEarned = save.plageMoneyEarned + skinEarnings.finalEarn,
                        )
                    } else {
                        save.copy(bestDistance = maxOf(save.bestDistance, result.distanceMeters))
                    }
                    updated = updated.copy(
                        money = updated.money + skinEarnings.finalEarn,
                        totalMoneyEarned = updated.totalMoneyEarned + skinEarnings.finalEarn,
                        totalThrows = updated.totalThrows + 1,
                        hasJackpot = updated.hasJackpot || skinEarnings.hasJackpot,
                    )
                    if (isSkidding) updated = Skid.applyDurabilityCost(updated)
                    beachOutcome?.let { outcome ->
                        if (outcome.parasolBounced) updated = updated.copy(plageParasolBounces = updated.plageParasolBounces + 1)
                        if (outcome.towelFound) updated = updated.copy(plageTowelsFound = updated.plageTowelsFound + 1)
                        if (outcome.castleCrushed) updated = updated.copy(plageCastlesCrushed = updated.plageCastlesCrushed + 1)
                    }

                    // Progression des défis quotidiens (voir bumpDailyChallenge()
                    // côté web) : nombre de lancers, distance (record du jour),
                    // distance cumulée, argent gagné, lancers parfaits.
                    updated = DailyChallenges.ensure(updated, LocalDate.now().toString(), totalPuissance, totalVitesse)
                    updated = DailyChallenges.bump(updated, "throws", 1.0, BumpMode.ADD)
                    updated = DailyChallenges.bump(updated, "distance", result.distanceMeters, BumpMode.MAX)
                    updated = DailyChallenges.bump(updated, "distanceCumul", result.distanceMeters, BumpMode.ADD)
                    updated = DailyChallenges.bump(updated, "earn", skinEarnings.finalEarn.toDouble(), BumpMode.ADD)
                    if (result.isPerfect) updated = DailyChallenges.bump(updated, "perfect", 1.0, BumpMode.ADD)
                    if (isSkidding) updated = DailyChallenges.bump(updated, "skid", 1.0, BumpMode.ADD)
                    if (result.isPerfect) updated = updated.copy(hasPerfectThrow = true)

                    onSaveChange(Achievements.apply(updated))
                }

                when {
                    !flightFinished -> Text("En vol...")
                    isSkidding && !skidFinished -> Text("Dérapage dans la poussière rouge...")
                    else -> {
                        Text("Distance : ${"%.1f".format(result.distanceMeters)} m")
                        if (result.isPerfect) Text("✨ Lancer parfait !")
                        if (isSkidding) Text("💥 Dérapage : -${Skid.DURABILITY_COST} durabilité")
                        earnings?.coinMultiplier?.let { Text("🪙 Multiplicateur x$it !") }
                        earnings?.takeIf { it.vampireStolen > 0 }?.let {
                            Text("🦇 -${it.vampireStolen}$ volés par la malédiction (${it.vampireStealPct.toInt()}%)")
                        }
                        beachOutcome?.takeIf { it.parasolBounced }?.let { Text("⛱️ Rebond sur un parasol !") }
                        beachOutcome?.takeIf { it.towelFound }?.let { Text("🧺 Atterri dans une serviette !") }
                        beachOutcome?.takeIf { it.castleCrushed }?.let { Text("🏰 Un château s'est écroulé !") }
                        Text("Tape pour relancer.")
                    }
                }
            }
        }

        Button(onClick = { tap() }) {
            Text("Tap")
        }
    }
}

/**
 * Barre qui suit une oscillation dépendant du temps (voir PowerAndAccuracy),
 * recalculée à chaque frame tant que l'écran est composé. Utilise
 * System.currentTimeMillis() (pas l'horloge de frame de Compose, qui n'a
 * pas la même origine) pour rester sur exactement la même base de temps que
 * ThrowSequence — sans ça, la valeur affichée pendant l'animation
 * divergerait de celle réellement verrouillée au moment du tap.
 */
@Composable
private fun LiveOscillatingBar(
    startedAtMillis: Long,
    valueAt: (elapsedSeconds: Double) -> Double,
    toProgress: (Double) -> Float,
) {
    val liveValue = remember(startedAtMillis) { mutableDoubleStateOf(valueAt(0.0)) }

    LaunchedEffect(startedAtMillis) {
        while (true) {
            // La valeur du timestamp de frame n'est pas utilisée : withFrameNanos
            // sert juste à caler la boucle sur le rafraîchissement de l'écran
            // plutôt que de spin-looper en continu. L'écart réel se calcule à
            // partir de System.currentTimeMillis(), la même horloge que
            // ThrowSequence, pour que la valeur affichée ici corresponde
            // exactement à celle qui serait verrouillée en tapant maintenant.
            withFrameNanos { }
            val elapsedSeconds = (System.currentTimeMillis() - startedAtMillis) / 1000.0
            liveValue.doubleValue = valueAt(elapsedSeconds)
        }
    }

    LinearProgressIndicator(
        progress = { toProgress(liveValue.doubleValue) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
    )
}
