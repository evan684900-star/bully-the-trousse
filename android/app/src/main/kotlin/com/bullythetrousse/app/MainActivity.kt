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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.ThrowSequence
import com.bullythetrousse.core.ThrowState

/**
 * Deuxième tranche du portage natif : la vraie interaction en 3 taps
 * (idle → charge de puissance → charge de précision → lancé), portée
 * depuis handleTap()/lockPower()/lockAccuracyAndLaunch() côté web. Pas
 * encore d'animation de barre en temps réel ni de rendu de la trousse —
 * juste le texte d'état, pour valider la mécanique avant de l'habiller.
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
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
            }

            is ThrowState.ChargingAccuracy -> {
                Text("Puissance figée à ${(current.lockedPower * 100).toInt()}%.")
                Text("Précision en charge... tape pour lancer.")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
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
