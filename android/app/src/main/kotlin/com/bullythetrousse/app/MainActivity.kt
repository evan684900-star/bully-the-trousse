package com.bullythetrousse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.PowerAndAccuracy
import com.bullythetrousse.core.Shop
import com.bullythetrousse.core.Skid
import com.bullythetrousse.core.Skin
import com.bullythetrousse.core.SkinShop
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.Trail
import com.bullythetrousse.core.TrailShop
import com.bullythetrousse.core.Trails
import com.bullythetrousse.core.ThrowSequence
import com.bullythetrousse.core.ThrowState
import com.bullythetrousse.core.VolcanoCinematic

/**
 * Huitième tranche du portage natif : skins et traînées (Skins/Trails, voir
 * :core) — la physique et les gains utilisent maintenant les stats
 * effectives (niveau acheté + bonus du skin équipé, via SkinStats),
 * exactement comme totalPuissance()/totalVitesse() côté web. Toujours pas
 * de mécaniques spéciales par skin (pièce, rebonds, vampire...), ni de
 * rendu visuel différent par skin (voir android/README.md).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameRoot()
                }
            }
        }
    }
}

@Composable
fun GameRoot() {
    val context = LocalContext.current
    val repository = remember { SaveRepository(context) }
    var save by remember { mutableStateOf(repository.load()) }
    var showingVolcanoCinematic by remember { mutableStateOf(false) }

    fun updateSave(updated: GameSave) {
        save = updated
        repository.save(updated)
    }

    if (showingVolcanoCinematic) {
        VolcanoCinematicScreen(onFinished = { outcome ->
            updateSave(VolcanoCinematic.applyOutcome(save, outcome, System.currentTimeMillis()))
            showingVolcanoCinematic = false
        })
    } else {
        ThrowScreen(
            save = save,
            onSaveChange = ::updateSave,
            onStartVolcanoCinematic = { showingVolcanoCinematic = true },
        )
    }
}

@Composable
fun ThrowScreen(save: GameSave, onSaveChange: (GameSave) -> Unit, onStartVolcanoCinematic: () -> Unit) {
    val sequence = remember { ThrowSequence() }
    var state by remember { mutableStateOf<ThrowState>(sequence.state) }

    fun tap() {
        state = sequence.tap(SkinStats.totalPuissance(save), SkinStats.totalVitesse(save))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .clickable { tap() },
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("🎒 Bully the Trousse", style = MaterialTheme.typography.headlineMedium)
        Text("💰 ${save.money}   Record : ${"%.1f".format(save.bestDistance)} m")
        Text("Puissance ${SkinStats.totalPuissance(save)}   Vitesse ${SkinStats.totalVitesse(save)}   Durabilité ${save.durability}/${SkinStats.maxDurability(save)}")

        when (val current = state) {
            is ThrowState.Idle -> {
                ThrowCanvas(flightState = null)
                Text("Tape l'écran pour commencer à charger la puissance.")
            }

            is ThrowState.ChargingPower -> {
                ThrowCanvas(flightState = null)
                Text("Puissance en charge... tape pour la figer.")
                LiveOscillatingBar(
                    startedAtMillis = current.startedAtMillis,
                    valueAt = PowerAndAccuracy::powerFraction,
                    // powerFraction oscille dans [0.4, 1.0] : la barre reste
                    // donc toujours au moins un peu remplie.
                    toProgress = { it.toFloat() },
                )
            }

            is ThrowState.ChargingAccuracy -> {
                ThrowCanvas(flightState = null)
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
                var flightFinished by remember(result) { mutableStateOf(false) }
                val flightState: FlightState = animateFlight(result) { flightFinished = true }

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
                ThrowCanvas(flightState = displayedFlightState)

                val throwResolved = flightFinished && skidDecided && (!isSkidding || skidFinished)

                // Porté de onLanded()/persist() côté web : argent gagné, record,
                // nombre de lancers et coût en durabilité d'un dérapage éventuel,
                // mis à jour puis sauvegardés une seule fois par lancer, une fois
                // le vol ET un éventuel dérapage entièrement résolus.
                LaunchedEffect(throwResolved, result) {
                    if (!throwResolved) return@LaunchedEffect
                    val totalLevels = SkinStats.totalPuissance(save) + SkinStats.totalVitesse(save)
                    val earned = Economy.moneyEarned(result.distanceMeters, result.isPerfect, totalLevels)
                    var updated = save.copy(
                        money = save.money + earned,
                        totalMoneyEarned = save.totalMoneyEarned + earned,
                        bestDistance = maxOf(save.bestDistance, result.distanceMeters),
                        totalThrows = save.totalThrows + 1,
                    )
                    if (isSkidding) updated = Skid.applyDurabilityCost(updated)
                    onSaveChange(updated)
                }

                when {
                    !flightFinished -> Text("En vol...")
                    isSkidding && !skidFinished -> Text("Dérapage dans la poussière rouge...")
                    else -> {
                        Text("Distance : ${"%.1f".format(result.distanceMeters)} m")
                        if (result.isPerfect) Text("✨ Lancer parfait !")
                        if (isSkidding) Text("💥 Dérapage : -${Skid.DURABILITY_COST} durabilité")
                        Text("Tape pour relancer.")
                    }
                }
            }
        }

        Button(onClick = { tap() }) {
            Text("Tap")
        }

        ShopRow(save = save, onPurchase = onSaveChange)
        VolcanoRow(save = save, onStartVolcanoCinematic = onStartVolcanoCinematic)
        SkinsRow(save = save, onSaveChange = onSaveChange)
        TrailsRow(save = save, onSaveChange = onSaveChange)
    }
}

/**
 * Liste des trousses cosmétiques, portage de buildSkinCard() côté web :
 * bouton "Prix" si pas possédée, "Équiper" si possédée mais pas équipée,
 * "Équipée" (désactivé) sinon. Défilement horizontal plutôt qu'une vraie
 * boutique dédiée (voir android/README.md).
 */
@Composable
private fun SkinsRow(save: GameSave, onSaveChange: (GameSave) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Skins.ALL) { skin: Skin ->
            val owned = save.ownedSkins.contains(skin.id)
            val equipped = save.equippedSkin == skin.id
            CosmeticButton(
                label = skin.id,
                owned = owned,
                equipped = equipped,
                cost = skin.cost,
                onBuy = {
                    val result = SkinShop.buy(save, skin.id)
                    if (result is SkinShop.PurchaseResult.Success) onSaveChange(result.save)
                },
                onEquip = { onSaveChange(SkinShop.equip(save, skin.id)) },
            )
        }
    }
}

/**
 * Liste des traînées cosmétiques, portage de buildTrailCard() côté web :
 * contrairement à un skin, l'achat équipe directement (pas d'étape
 * "Équiper" séparée pour un achat).
 */
@Composable
private fun TrailsRow(save: GameSave, onSaveChange: (GameSave) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Trails.ALL) { trail: Trail ->
            val owned = save.ownedTrails.contains(trail.id)
            val equipped = save.equippedTrail == trail.id
            CosmeticButton(
                label = trail.id,
                owned = owned,
                equipped = equipped,
                cost = trail.cost,
                onBuy = {
                    val result = TrailShop.buy(save, trail.id)
                    if (result is TrailShop.PurchaseResult.Success) onSaveChange(result.save)
                },
                onEquip = { onSaveChange(TrailShop.equip(save, trail.id)) },
            )
        }
    }
}

@Composable
private fun CosmeticButton(label: String, owned: Boolean, equipped: Boolean, cost: Int, onBuy: () -> Unit, onEquip: () -> Unit) {
    when {
        equipped -> OutlinedButton(onClick = {}, enabled = false) { Text("✅ $label") }
        owned -> Button(onClick = onEquip) { Text(label) }
        else -> Button(onClick = onBuy) { Text("$label ($cost)") }
    }
}

/**
 * Bouton pour lancer la cinématique de déblocage du volcan, portage de
 * tryStartVolcanoCinematic() côté web : verrouillé tant que le monde est
 * débloqué, ou pendant les 10 minutes de cooldown après un échec.
 */
@Composable
private fun VolcanoRow(save: GameSave, onStartVolcanoCinematic: () -> Unit) {
    if (save.volcanUnlocked) return
    val cooldownRemainingMs = save.volcanFailedUntil - System.currentTimeMillis()
    if (cooldownRemainingMs > 0) {
        val minutes = cooldownRemainingMs / 60_000
        val seconds = (cooldownRemainingMs % 60_000) / 1000
        Text("🌋 Le volcan gronde encore... réessaie dans ${minutes} min ${seconds} s")
    } else {
        Button(onClick = onStartVolcanoCinematic, modifier = Modifier.fillMaxWidth()) {
            Text("🌋 Découvrir le volcan")
        }
    }
}

/**
 * Boutique Puissance/Vitesse, portage des deux boutons quasi-identiques de
 * la boutique web (voir Shop.buyPuissance/buyVitesse dans :core pour la
 * logique de débit/incrément exacte). Toujours affichée sous le bouton de
 * lancer, pas encore dans un écran dédié (voir android/README.md).
 */
@Composable
private fun ShopRow(save: GameSave, onPurchase: (GameSave) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                val result = Shop.buyPuissance(save)
                if (result is Shop.PurchaseResult.Success) onPurchase(result.save)
            },
        ) {
            Text("⚡ Puissance Nv.${save.puissanceLevel} (${Economy.upgradeCost(save.puissanceLevel)})")
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                val result = Shop.buyVitesse(save)
                if (result is Shop.PurchaseResult.Success) onPurchase(result.save)
            },
        ) {
            Text("💨 Vitesse Nv.${save.vitesseLevel} (${Economy.upgradeCost(save.vitesseLevel)})")
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
