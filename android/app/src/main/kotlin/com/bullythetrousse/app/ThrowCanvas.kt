package com.bullythetrousse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Camera
import com.bullythetrousse.core.FlightSimulator
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.PhysicsConstants
import com.bullythetrousse.core.ThrowResult
import com.bullythetrousse.core.rotationSpeed
import com.bullythetrousse.core.toInitialFlightState

/**
 * Rendu Canvas du ciel/sol et de la trousse en vol, portage visuel du fond
 * dessiné par drawBackground()/gameLoop() côté web. Pas encore de décor
 * (parasols, serviettes...) ni de sprite réel pour la trousse — un rectangle
 * qui tourne, en attendant les assets (voir android/README.md, prochaine
 * étape "skins et traînées").
 *
 * `flightState` vaut `null` tant qu'aucun lancer n'est en vol : on affiche
 * alors juste le sol, avec la trousse posée à l'origine.
 */
@Composable
fun ThrowCanvas(flightState: FlightState?, groundVerticalFraction: Float = 0.68f) {
    val skyColor = MaterialTheme.colorScheme.tertiaryContainer
    val groundColor = MaterialTheme.colorScheme.secondaryContainer
    val trousseColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val groundScreenY = size.height * groundVerticalFraction
        drawRect(color = skyColor, size = androidx.compose.ui.geometry.Size(size.width, groundScreenY))
        drawRect(
            color = groundColor,
            topLeft = Offset(0f, groundScreenY),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - groundScreenY),
        )

        // Caméra centrée sur worldX=0 tant qu'il n'y a pas de vol en cours,
        // sinon elle suit la trousse (voir Camera.followX, portage de la
        // logique de gameLoop côté web).
        val cameraX = if (flightState != null) Camera.followX(flightState.worldX, size.width.toDouble()) else 0.0
        val worldY = flightState?.worldY ?: 0.0
        val screen = Camera.worldToScreen(
            worldX = flightState?.worldX ?: 0.0,
            worldY = worldY,
            cameraX = cameraX,
            groundScreenY = groundScreenY.toDouble(),
        )

        val trousseSizePx = 28.dp.toPx()
        rotate(
            degrees = ((flightState?.rotation ?: 0.0) * 180.0 / Math.PI).toFloat(),
            pivot = Offset(screen.sx.toFloat(), screen.sy.toFloat()),
        ) {
            drawRect(
                color = trousseColor,
                topLeft = Offset(screen.sx.toFloat() - trousseSizePx / 2, screen.sy.toFloat() - trousseSizePx / 2),
                size = androidx.compose.ui.geometry.Size(trousseSizePx, trousseSizePx),
            )
        }
    }
}

/**
 * Anime un [FlightState] à partir d'un [ThrowResult] fraîchement lancé,
 * pas à pas via [FlightSimulator], jusqu'à l'atterrissage. `onLanded` est
 * appelé une seule fois, quand `hasLanded` devient vrai.
 */
@Composable
fun animateFlight(result: ThrowResult, onLanded: () -> Unit): FlightState {
    var state by remember(result) { mutableStateOf(result.toInitialFlightState()) }
    val rotSpeed = remember(result) { rotationSpeed(result.initialSpeed) }

    LaunchedEffect(result) {
        var lastFrameMillis = System.currentTimeMillis()
        while (!state.hasLanded) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            state = FlightSimulator.step(state, result.effectiveGravity, rotSpeed, dt)
        }
        onLanded()
    }

    return state
}
