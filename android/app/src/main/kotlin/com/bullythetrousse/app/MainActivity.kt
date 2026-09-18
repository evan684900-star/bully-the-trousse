package com.bullythetrousse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.bullythetrousse.core.PowerAndAccuracy
import com.bullythetrousse.core.ThrowSequence
import com.bullythetrousse.core.ThrowState

/**
 * Cinquième tranche du portage natif : la sauvegarde locale (GameSave, voir
 * :core) est chargée au lancement et mise à jour/persistée (argent gagné,
 * meilleure distance, nombre de lancers) à chaque atterrissage, comme
 * persist() côté web. Toujours une seule trousse fixe (pas de boutique de
 * niveaux/skins branchée), mais save.puissanceLevel/vitesseLevel pilotent
 * déjà réellement la physique du lancer.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ThrowScreen()
                }
            }
        }
    }
}

@Composable
fun ThrowScreen() {
    val context = LocalContext.current
    val repository = remember { SaveRepository(context) }
    var save by remember { mutableStateOf(repository.load()) }

    val sequence = remember { ThrowSequence() }
    var state by remember { mutableStateOf<ThrowState>(sequence.state) }

    fun tap() {
        state = sequence.tap(save.puissanceLevel, save.vitesseLevel)
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
                ThrowCanvas(flightState = flightState)

                // Porté de onLanded()/persist() côté web : argent gagné, record et
                // nombre de lancers mis à jour puis sauvegardés dès l'atterrissage,
                // une seule fois par lancer (clé = ce `result` précis, voir remember).
                LaunchedEffect(flightFinished, result) {
                    if (!flightFinished) return@LaunchedEffect
                    val totalLevels = save.puissanceLevel + save.vitesseLevel
                    val earned = Economy.moneyEarned(result.distanceMeters, result.isPerfect, totalLevels)
                    save = save.copy(
                        money = save.money + earned,
                        totalMoneyEarned = save.totalMoneyEarned + earned,
                        bestDistance = maxOf(save.bestDistance, result.distanceMeters),
                        totalThrows = save.totalThrows + 1,
                    )
                    repository.save(save)
                }

                if (flightFinished) {
                    Text("Distance : ${"%.1f".format(result.distanceMeters)} m")
                    if (result.isPerfect) Text("✨ Lancer parfait !")
                    Text("Tape pour relancer.")
                } else {
                    Text("En vol...")
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
