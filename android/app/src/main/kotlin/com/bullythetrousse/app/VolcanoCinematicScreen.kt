package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.CinePhase
import com.bullythetrousse.core.RockState
import com.bullythetrousse.core.VolcanoCineOutcome
import com.bullythetrousse.core.VolcanoCineState
import com.bullythetrousse.core.VolcanoCinematic

/**
 * Cinématique de déblocage du volcan, portage visuel simplifié de la
 * séquence CINE_TIMES côté web : le déroulement des phases (durées,
 * mini-jeu d'esquive des roches, QTE de clics) vient de [VolcanoCinematic]
 * (`:core`, testé), ici on ne fait qu'afficher la phase courante et
 * transmettre les entrées (esquive, clic). Pas encore l'habillage complet
 * du web (secousse d'écran, éclair, décor 3D du volcan) — un fond de
 * couleur qui change avec la phase, en attendant.
 */
@Composable
fun VolcanoCinematicScreen(onFinished: (VolcanoCineOutcome) -> Unit) {
    var state by remember { mutableStateOf(VolcanoCineState()) }

    LaunchedEffect(Unit) {
        var lastFrameMillis = System.currentTimeMillis()
        while (state.outcome == null) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            state = VolcanoCinematic.step(state, dt)
        }
        onFinished(state.outcome!!)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(phaseColor(state.phase))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(phaseLabel(state.phase), style = MaterialTheme.typography.headlineSmall)

        when (state.phase) {
            CinePhase.ROCKS -> {
                Text("Roche ${(state.rockIndex + 1).coerceAtMost(VolcanoCinematic.ROCKS)}/${VolcanoCinematic.ROCKS}")
                Text("❤️".repeat(state.lives + 1) + "🤍".repeat(2 - state.lives))
                LaneIndicator(lane = state.lane, warning = state.rockState == RockState.WARN)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { state = VolcanoCinematic.move(state, -1) }) { Text("◀") }
                    Button(onClick = { state = VolcanoCinematic.move(state, 1) }) { Text("▶") }
                }
            }

            CinePhase.DESCENT -> {
                Text("Tape vite pour ralentir la chute !")
                LinearProgressIndicator(
                    progress = { state.clicks.toFloat() / VolcanoCinematic.CLICK_TARGET },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Button(onClick = { state = VolcanoCinematic.click(state) }) { Text("TAP (${state.clicks}/${VolcanoCinematic.CLICK_TARGET})") }
            }

            CinePhase.DEATH -> Text("💥 Raté... nouvelle tentative dans 10 minutes.")
            CinePhase.OUTRO -> Text("🌋 Monde Volcan débloqué !")
            else -> Unit
        }
    }
}

@Composable
private fun LaneIndicator(lane: Int, warning: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in -1..1) {
            val active = l == lane
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .background(
                        when {
                            active && warning -> Color(0xFFE74C3C)
                            active -> Color(0xFFE67E22)
                            else -> Color(0x33000000)
                        },
                    ),
            ) {}
        }
    }
}

private fun phaseColor(phase: CinePhase): Color = when (phase) {
    CinePhase.FADE -> Color(0xFF1A1A1A)
    CinePhase.APPROACH -> Color(0xFF3A2A1F)
    CinePhase.QUAKE -> Color(0xFF5A2E1F)
    CinePhase.ERUPTION -> Color(0xFFE74C3C)
    CinePhase.ASCENT, CinePhase.STABILIZE -> Color(0xFF7EC8E3)
    CinePhase.ROCKS -> Color(0xFF6F4A2F)
    CinePhase.DESCENT -> Color(0xFF4A6F8F)
    CinePhase.LANDING, CinePhase.OUTRO -> Color(0xFF8F5A2F)
    CinePhase.DEATH -> Color(0xFF1A1A1A)
}

private fun phaseLabel(phase: CinePhase): String = when (phase) {
    CinePhase.FADE -> "..."
    CinePhase.APPROACH -> "Approche du volcan"
    CinePhase.QUAKE -> "🌋 Tremblement de terre !"
    CinePhase.ERUPTION -> "💥 Éruption !"
    CinePhase.ASCENT -> "Projection dans le ciel"
    CinePhase.STABILIZE -> "Stabilisation"
    CinePhase.ROCKS -> "Esquive les roches !"
    CinePhase.DESCENT -> "Descente"
    CinePhase.LANDING -> "Atterrissage"
    CinePhase.OUTRO -> "🌋 Monde Volcan"
    CinePhase.DEATH -> "💥"
}
