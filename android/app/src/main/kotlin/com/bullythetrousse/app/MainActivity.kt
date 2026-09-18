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
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.PowerAndAccuracy
import com.bullythetrousse.core.ThrowSequence
import com.bullythetrousse.core.ThrowState

/**
 * Troisième tranche du portage natif : les barres de puissance/précision
 * s'animent maintenant en temps réel pendant la charge (avant, seul le
 * texte d'état s'affichait), en suivant les mêmes oscillations que
 * PowerAndAccuracy — donc la valeur affichée à l'écran au moment du tap
 * est bien celle qui sera verrouillée. Toujours pas de rendu de la
 * trousse (voir android/README.md).
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
    // Niveaux fixes pour l'instant (pas encore de sauvegarde/boutique
    // portés) : voir android/README.md pour la suite prévue.
    val puissanceLevel = 0
    val vitesseLevel = 0

    val sequence = remember { ThrowSequence() }
    var state by remember { mutableStateOf<ThrowState>(sequence.state) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .clickable { state = sequence.tap(puissanceLevel, vitesseLevel) },
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("🎒 Bully the Trousse", style = MaterialTheme.typography.headlineMedium)

        when (val current = state) {
            is ThrowState.Idle ->
                Text("Tape l'écran pour commencer à charger la puissance.")

            is ThrowState.ChargingPower -> {
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
                Text("Distance : ${"%.1f".format(result.distanceMeters)} m")
                if (result.isPerfect) Text("✨ Lancer parfait !")
                Text("Tape pour relancer.")
            }
        }

        Button(onClick = { state = sequence.tap(puissanceLevel, vitesseLevel) }) {
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
