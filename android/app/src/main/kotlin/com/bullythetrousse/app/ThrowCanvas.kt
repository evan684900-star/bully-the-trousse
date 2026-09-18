package com.bullythetrousse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Camera
import com.bullythetrousse.core.CourDecor
import com.bullythetrousse.core.FlightSimulator
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.Skid
import com.bullythetrousse.core.ThrowResult
import com.bullythetrousse.core.rotationSpeed
import com.bullythetrousse.core.toInitialFlightState
import kotlin.math.max

/**
 * Rendu Canvas du monde "Cour d'école" (ciel, bâtiments, arbres, sol) et de
 * la trousse en vol, portage visuel de drawBackground()/drawBuilding()/
 * drawTree()/drawTrousse() côté web. Le placement du décor vient de
 * [CourDecor] (testé dans :core) ; ici on ne fait QUE le dessin. Toujours
 * pas de vrai sprite pour la trousse (juste une forme vectorielle stylisée,
 * comme le fait le web pour les skins "coin"/"iron" — voir android/README.md
 * pour les prochaines étapes : skins et traînées).
 *
 * `flightState` vaut `null` tant qu'aucun lancer n'est en vol : on affiche
 * alors juste le décor, avec la trousse posée à l'origine.
 */
@Composable
fun ThrowCanvas(flightState: FlightState?, groundVerticalFraction: Float = 0.68f) {
    val skyTop = Color(0xFF7EC8E3)
    val skyBottom = Color(0xFFD8F3FF)
    val groundTop = Color(0xFF9AA0AB)
    val groundBottom = Color(0xFF6F7480)

    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val groundScreenY = size.height * groundVerticalFraction

        // Ciel (dégradé) puis sol (dégradé), comme drawBackground() côté web.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(skyTop, skyBottom),
                startY = 0f,
                endY = groundScreenY,
            ),
            size = Size(size.width, groundScreenY),
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(groundTop, groundBottom),
                startY = groundScreenY,
                endY = size.height,
            ),
            topLeft = Offset(0f, groundScreenY),
            size = Size(size.width, size.height - groundScreenY),
        )

        // Caméra centrée sur worldX=0 tant qu'il n'y a pas de vol en cours,
        // sinon elle suit la trousse (voir Camera.followX, portage de la
        // logique de gameLoop côté web).
        val cameraX = if (flightState != null) Camera.followX(flightState.worldX, size.width.toDouble()) else 0.0

        // Bâtiments au loin (parallaxe) puis arbres sur la ligne d'horizon,
        // dans le même ordre que drawBackground() (les arbres recouvrent
        // les bâtiments qui défilent derrière eux).
        for (index in CourDecor.visibleBuildingIndices(cameraX, size.width.toDouble())) {
            val bx = CourDecor.buildingScreenX(index, cameraX)
            if (bx < -220.0 || bx > size.width + 40.0) continue
            drawCourBuilding(bx.toFloat(), groundScreenY, index)
        }
        for (index in CourDecor.visibleTreeIndices(cameraX, size.width.toDouble())) {
            val worldPos = CourDecor.treeWorldX(index)
            val sx = worldPos - cameraX
            if (sx < -40.0 || sx > size.width + 40.0) continue
            drawCourTree(sx.toFloat(), groundScreenY + 6f, index)
        }

        val worldY = flightState?.worldY ?: 0.0
        val screen = Camera.worldToScreen(
            worldX = flightState?.worldX ?: 0.0,
            worldY = worldY,
            cameraX = cameraX,
            groundScreenY = groundScreenY.toDouble(),
        )
        rotate(
            degrees = ((flightState?.rotation ?: 0.0) * 180.0 / Math.PI).toFloat(),
            pivot = Offset(screen.sx.toFloat(), screen.sy.toFloat()),
        ) {
            drawTrousse(centerX = screen.sx.toFloat(), centerY = screen.sy.toFloat(), size = 30.dp.toPx())
        }
    }
}

/**
 * Dessine un bâtiment d'école en parallaxe, portage de drawBuilding() côté
 * web : façade brique/pierre alternée (seed pair/impair), bande de toit,
 * grille de fenêtres dont certaines "allumées", porte d'entrée.
 */
private fun DrawScope.drawCourBuilding(bx: Float, groundY: Float, seed: Int) {
    val brick = CourDecor.seededRand(seed.toDouble()) > 0.5
    val height = (130.0 + CourDecor.seededRand(seed + 1.0) * 50.0).toFloat()
    val bw = 170f
    val wallColor = if (brick) Color(0xFFC4786E).copy(alpha = 0.5f) else Color(0xFFE6E6EB).copy(alpha = 0.6f)
    val roofColor = if (brick) Color(0xFF783C37).copy(alpha = 0.6f) else Color(0xFFA08C78).copy(alpha = 0.55f)

    drawRect(color = wallColor, topLeft = Offset(bx, groundY - height), size = Size(bw, height))
    drawRect(color = roofColor, topLeft = Offset(bx - 6f, groundY - height - 12f), size = Size(bw + 12f, 16f))

    val cols = 4
    val rows = max(2, ((height - 30f) / 38f).toInt())
    for (c in 0 until cols) {
        for (r in 0 until rows) {
            val lit = CourDecor.seededRand(seed * 100.0 + c * 10.0 + r) > 0.72
            val windowColor = if (lit) Color(0xFFFFE696).copy(alpha = 0.75f) else Color(0xFF5A6E8C).copy(alpha = 0.4f)
            drawRect(
                color = windowColor,
                topLeft = Offset(bx + 14f + c * 40f, groundY - height + 16f + r * 38f),
                size = Size(24f, 24f),
            )
        }
    }

    drawRect(
        color = Color(0xFF5A3C2D).copy(alpha = 0.6f),
        topLeft = Offset(bx + bw / 2f - 14f, groundY - 34f),
        size = Size(28f, 34f),
    )
}

/**
 * Dessine un arbre (tronc + 3 boules de feuillage + ombre), portage de
 * drawTree() côté web.
 */
private fun DrawScope.drawCourTree(sx: Float, groundY: Float, seed: Int) {
    val scale = (0.85 + CourDecor.seededRand(seed.toDouble()) * 0.4).toFloat()
    val trunkH = 34f * scale
    val trunkW = 10f * scale
    drawRect(color = Color(0xFF7A5230), topLeft = Offset(sx - trunkW / 2f, groundY - trunkH), size = Size(trunkW, trunkH))

    val greens = listOf(Color(0xFF4A8F3C), Color(0xFF3F7F33), Color(0xFF5AA347))
    val green = greens[(CourDecor.seededRand(seed + 2.0) * 3).toInt().coerceIn(0, 2)]
    drawCircle(color = green, radius = 24f * scale, center = Offset(sx, groundY - trunkH - 22f * scale))
    drawCircle(color = green, radius = 17f * scale, center = Offset(sx - 16f * scale, groundY - trunkH - 12f * scale))
    drawCircle(color = green, radius = 17f * scale, center = Offset(sx + 16f * scale, groundY - trunkH - 12f * scale))
    drawCircle(
        color = Color.Black.copy(alpha = 0.08f),
        radius = 14f * scale,
        center = Offset(sx + 8f * scale, groundY - trunkH - 18f * scale),
    )
}

/**
 * Silhouette vectorielle de la trousse : pas encore le sprite réel du web
 * (une image bitmap, voir trousseImg côté JS), mais une forme reconnaissable
 * plutôt qu'un simple carré — corps arrondi, rabat et fermeture éclair,
 * dessinés en pur Canvas comme le sont déjà les skins "coin"/"iron" côté web.
 */
private fun DrawScope.drawTrousse(centerX: Float, centerY: Float, size: Float) {
    val topLeft = Offset(centerX - size / 2f, centerY - size / 2f)
    val bodySize = Size(size, size * 0.62f)
    val bodyTopLeft = Offset(topLeft.x, centerY - bodySize.height / 2f)

    drawRoundRect(
        color = Color(0xFFE67E22),
        topLeft = bodyTopLeft,
        size = bodySize,
        cornerRadius = CornerRadius(size * 0.22f, size * 0.22f),
    )
    // rabat plus sombre sur le tiers supérieur, pour donner du volume
    drawRoundRect(
        color = Color(0xFFC96A1A),
        topLeft = bodyTopLeft,
        size = Size(bodySize.width, bodySize.height * 0.32f),
        cornerRadius = CornerRadius(size * 0.22f, size * 0.22f),
    )
    // ligne de fermeture éclair
    drawLine(
        color = Color(0xFFFFF3E0),
        start = Offset(bodyTopLeft.x + size * 0.08f, centerY),
        end = Offset(bodyTopLeft.x + bodySize.width - size * 0.08f, centerY),
        strokeWidth = size * 0.035f,
    )
    // tirette de la fermeture éclair
    drawCircle(color = Color(0xFFFFF3E0), radius = size * 0.05f, center = Offset(centerX, centerY))
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

/**
 * Anime le dérapage du monde Volcan (voir [Skid], `:core`) : la trousse
 * continue de glisser au sol depuis [landingWorldX] jusqu'à la cible tirée
 * au hasard, pas à pas. `onFinished` est appelé une seule fois, avec la
 * position finale, quand la glissade s'arrête.
 */
@Composable
fun animateSkid(landingWorldX: Double, onFinished: (finalWorldX: Double) -> Unit): FlightState {
    val target = remember(landingWorldX) { Skid.targetWorldX(landingWorldX) }
    var state by remember(landingWorldX) { mutableStateOf(FlightState(worldX = landingWorldX, worldY = 0.0, vx = 0.0, vy = 0.0)) }

    LaunchedEffect(landingWorldX) {
        var lastFrameMillis = System.currentTimeMillis()
        var finished = false
        while (!finished) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            val step = Skid.step(worldX = state.worldX, targetWorldX = target, rotation = state.rotation, dt = dt)
            state = state.copy(worldX = step.worldX, rotation = step.rotation)
            finished = step.finished
        }
        onFinished(state.worldX)
    }

    return state
}
