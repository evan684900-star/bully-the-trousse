package com.bullythetrousse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.ThrowInput
import com.bullythetrousse.core.ThrowPhysics

/**
 * Première tranche verticale du portage natif : un seul écran qui prouve
 * que ":app" (Compose) parle bien à ":core" (physique/économie), avant de
 * construire le vrai gameplay (charge de puissance, visée, rendu de la
 * trousse...). Volontairement minimal.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ThrowPreviewScreen()
                }
            }
        }
    }
}

@Composable
fun ThrowPreviewScreen() {
    var lastDistance by remember { mutableStateOf<Double?>(null) }
    var lastEarn by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("🎒 Bully the Trousse", style = MaterialTheme.typography.headlineMedium)
        Text("Portage natif — première tranche : physique + économie de :core")

        Button(onClick = {
            // Lancer "au hasard" juste pour prouver le câblage bout en bout ;
            // la vraie interaction (charge de puissance, visée) viendra dans
            // une prochaine tranche.
            val result = ThrowPhysics.simulateThrow(
                ThrowInput(puissanceLevel = 0, vitesseLevel = 0, lockedPower = 1.0, accuracyValue = 0.0)
            )
            lastDistance = result.distanceMeters
            lastEarn = Economy.moneyEarned(result.distanceMeters, result.isPerfect, totalLevels = 0)
        }) {
            Text("🚀 Lancer (test)")
        }

        lastDistance?.let { distance ->
            Text("Distance : ${"%.1f".format(distance)} m")
        }
        lastEarn?.let { earn ->
            Text("Gagné : $earn \$")
        }
    }
}
