package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.BeachCinePhase
import com.bullythetrousse.core.BeachCineState
import com.bullythetrousse.core.BeachCinematic

/**
 * Cinématique de déblocage de la plage, portage volontairement simplifié
 * de la séquence `BCINE` côté web : le séquencement des phases
 * (BeachCinematic, `:core`) reprend les vraies durées de `BCINE`, mais les
 * mini-séquences interactives du web (course-poursuite de crabes, visée à
 * ne pas poser dans la "zone verte", dialogues à valider un par un) ne
 * changent JAMAIS l'issue côté web (`bcFinish()` est toujours atteint,
 * contrairement à la cinématique du volcan qui peut échouer) — donc ici,
 * la séquence avance toute seule, phase après phase, avec juste un texte
 * qui change, jusqu'à l'arrivée sur la plage.
 */
@Composable
fun BeachCinematicScreen(onFinished: () -> Unit) {
    var state by remember { mutableStateOf(BeachCineState()) }

    LaunchedEffect(Unit) {
        var lastFrameMillis = System.currentTimeMillis()
        while (!state.finished) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            state = BeachCinematic.step(state, dt)
        }
        onFinished()
    }

    // Fond : le ciel/sol du site, avec le voile de nuit par-dessus.
    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        SkyAnimation(heightFraction = 0.68f)

        // #bcine-aim : consigne de visée, en rouge et très grasse.
        if (state.phase == BeachCinePhase.AIM) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 112.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    "VISE !!!",
                    color = Color(0xFFFF3B30),
                    fontSize = 32.sp, // clamp(24px, 6vw, 40px)
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
        }

        // #bcine-dialog .box : le texte de la phase dans un panneau centré.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PanelBg)
                    .border(2.dp, PanelBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    phaseLabel(state.phase),
                    color = TextColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "la cinématique se déroule toute seule",
                    color = TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun phaseLabel(phase: BeachCinePhase): String = when (phase) {
    BeachCinePhase.FADE -> "..."
    BeachCinePhase.HAIL -> "La trousse hèle le bus"
    BeachCinePhase.BUS_IN -> "🚌 Le bus arrive"
    BeachCinePhase.DOOR_OPEN -> "La porte s'ouvre"
    BeachCinePhase.BOARDING -> "Embarquement"
    BeachCinePhase.SEATED -> "En route..."
    BeachCinePhase.BUS_OUT -> "Le bus s'éloigne"
    BeachCinePhase.IRIS -> "..."
    BeachCinePhase.BLACK -> "..."
    BeachCinePhase.ARRIVE -> "🏖️ Arrivée !"
    BeachCinePhase.BUS_LEAVE -> "Le bus repart"
    BeachCinePhase.PIVOT -> "Elle regarde autour d'elle"
    BeachCinePhase.COLLAPSE -> "Elle s'affale sur le sable, soulagée"
    BeachCinePhase.FORGOT -> "💸 \"Elle a oublié son argent...\""
    BeachCinePhase.CHEH -> "\"Cheh.\""
    BeachCinePhase.AFTER_CHEH -> "..."
    BeachCinePhase.CRAB -> "🦀 Un crabe sort du sable !"
    BeachCinePhase.BACK_AWAY -> "Recul prudent"
    BeachCinePhase.SWARM -> "🦀🦀🦀 D'autres crabes arrivent"
    BeachCinePhase.CHASE -> "Course-poursuite !"
    BeachCinePhase.AIM_INTRO -> "Elle reprend son souffle"
    BeachCinePhase.AIM -> "Elle vise..."
    BeachCinePhase.FLY1 -> "Envol !"
    BeachCinePhase.PARASOL -> "⛱️ Rebond sur un parasol"
    BeachCinePhase.FLY2 -> "200 m plus loin..."
    BeachCinePhase.CASTLE -> "🏰 Un château s'écroule"
    BeachCinePhase.FLY3 -> "Encore un peu plus loin..."
    BeachCinePhase.TOWEL -> "🧺 Atterrissage dans une serviette"
    BeachCinePhase.FLY4 -> "Dernier vol..."
    BeachCinePhase.OUTRO -> "🏖️ Bienvenue dans le monde Plage !"
}
