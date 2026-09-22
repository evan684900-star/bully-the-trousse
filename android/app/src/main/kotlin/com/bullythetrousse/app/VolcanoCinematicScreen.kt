package com.bullythetrousse.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(modifier = Modifier.fillMaxSize().background(phaseColor(state.phase))) {
        // #cine-lives : les coeurs, centrés tout en haut, très espacés.
        if (state.phase == CinePhase.ROCKS) {
            Box(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    "❤️".repeat(state.lives + 1) + "🤍".repeat(2 - state.lives),
                    fontSize = 22.sp,
                    letterSpacing = 6.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                phaseLabel(state.phase),
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            when (state.phase) {
                CinePhase.ROCKS -> {
                    Text(
                        "Roche ${(state.rockIndex + 1).coerceAtMost(VolcanoCinematic.ROCKS)}/${VolcanoCinematic.ROCKS}",
                        color = TextDim,
                        fontSize = 13.sp,
                    )
                    // #cine-warn : triangle d'alerte qui clignote juste avant
                    // qu'une roche tombe sur la voie visée.
                    if (state.rockState == RockState.WARN) {
                        val blink = rememberInfiniteTransition(label = "warn")
                        val alpha by blink.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.15f,
                            animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
                            label = "warnBlink",
                        )
                        Text("⚠️", fontSize = 56.sp, modifier = Modifier.alpha(alpha))
                    }
                    LaneIndicator(lane = state.lane, warning = state.rockState == RockState.WARN)
                    // #cine-sides : les deux flèches de déplacement.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GameButton("◀", secondary = true) { state = VolcanoCinematic.move(state, -1) }
                        GameButton("▶", secondary = true) { state = VolcanoCinematic.move(state, 1) }
                    }
                }

                CinePhase.DESCENT -> {
                    // #cine-clickqte : la consigne, la jauge, puis le compteur.
                    Text("Clique vite pour reprendre ton équilibre !", color = TextColor, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x8C0A0C14))
                            .border(2.dp, PanelBorder, RoundedCornerShape(999.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((state.clicks.toFloat() / VolcanoCinematic.CLICK_TARGET).coerceIn(0f, 1f))
                                .background(Accent),
                        )
                    }
                    Text(
                        "${state.clicks} / ${VolcanoCinematic.CLICK_TARGET}",
                        color = TextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    GameButton("TAP") { state = VolcanoCinematic.click(state) }
                }

                CinePhase.DEATH -> Text(
                    "💥 Raté... nouvelle tentative dans 10 minutes.",
                    color = TextColor,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                CinePhase.OUTRO -> Text(
                    "🌋 Monde Volcan débloqué !",
                    color = Accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                else -> Unit
            }
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
                            active && warning -> Accent2
                            active -> Accent
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
