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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Beach
import com.bullythetrousse.core.BeachDecor
import com.bullythetrousse.core.BeachEvents
import com.bullythetrousse.core.BeachLandingOutcome
import com.bullythetrousse.core.BeachPropType
import com.bullythetrousse.core.Camera
import com.bullythetrousse.core.CourDecor
import com.bullythetrousse.core.FlightSimulator
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.Skid
import com.bullythetrousse.core.ThrowResult
import com.bullythetrousse.core.VolcanoDecor
import com.bullythetrousse.core.rotationSpeed
import com.bullythetrousse.core.toInitialFlightState
import kotlin.math.max

/** Palette de fond (ciel haut/bas, sol haut/bas) pour un monde donné, portage
 *  des dégradés de drawBackground()/drawVolcanoBackground()/
 *  drawBeachBackground() côté web (thème "jour" uniquement — voir
 *  android/README.md, le thème clair/sombre n'est pas encore porté). */
private data class WorldPalette(val skyTop: Color, val skyBottom: Color, val groundTop: Color, val groundBottom: Color)

private fun paletteFor(world: String): WorldPalette = when (world) {
    "volcans" -> WorldPalette(Color(0xFF8D2415), Color(0xFFDD7C4C), Color(0xFF8B3520), Color(0xFF4D1A10))
    "plage" -> WorldPalette(Color(0xFF4EC3EA), Color(0xFFFFE9BD), Color(0xFFF2D98B), Color(0xFFCFA958))
    else -> WorldPalette(Color(0xFF7EC8E3), Color(0xFFD8F3FF), Color(0xFF9AA0AB), Color(0xFF6F7480))
}

/**
 * Rendu Canvas du monde en cours (ciel, décor, sol) et de la trousse en vol,
 * portage visuel de drawBackground()/drawVolcanoBackground()/
 * drawBeachBackground() et de leurs fonctions de décor associées côté web.
 * Le placement du décor vient de [CourDecor]/[VolcanoDecor]/[BeachDecor]
 * (testés dans `:core`) ; ici on ne fait QUE le dessin. Toujours pas de
 * vrai sprite pour la trousse (juste une forme vectorielle stylisée, comme
 * le fait le web pour les skins "coin"/"iron").
 *
 * `flightState` vaut `null` tant qu'aucun lancer n'est en vol : on affiche
 * alors juste le décor, avec la trousse posée à l'origine. `world` est
 * `save.currentWorld` ("cour", "volcans" ou "plage").
 */
@Composable
fun ThrowCanvas(flightState: FlightState?, world: String = "cour", groundVerticalFraction: Float = 0.68f) {
    val palette = paletteFor(world)

    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val groundScreenY = size.height * groundVerticalFraction

        // Ciel (dégradé) puis sol (dégradé), comme drawBackground() côté web.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(palette.skyTop, palette.skyBottom),
                startY = 0f,
                endY = groundScreenY,
            ),
            size = Size(size.width, groundScreenY),
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(palette.groundTop, palette.groundBottom),
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

        when (world) {
            "volcans" -> drawVolcanoDecor(cameraX, size.width.toDouble(), groundScreenY, size.height)
            "plage" -> drawBeachDecor(cameraX, size.width.toDouble(), groundScreenY)
            else -> drawCourDecor(cameraX, size.width.toDouble(), groundScreenY)
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

/** Bâtiments en parallaxe puis arbres sur la ligne d'horizon, portage du
 *  corps de drawBackground() côté web (les arbres recouvrent les bâtiments
 *  qui défilent derrière eux, d'où l'ordre). */
private fun DrawScope.drawCourDecor(cameraX: Double, screenWidth: Double, groundScreenY: Float) {
    for (index in CourDecor.visibleBuildingIndices(cameraX, screenWidth)) {
        val bx = CourDecor.buildingScreenX(index, cameraX)
        if (bx < -220.0 || bx > screenWidth + 40.0) continue
        drawCourBuilding(bx.toFloat(), groundScreenY, index)
    }
    for (index in CourDecor.visibleTreeIndices(cameraX, screenWidth)) {
        val worldPos = CourDecor.treeWorldX(index)
        val sx = worldPos - cameraX
        if (sx < -40.0 || sx > screenWidth + 40.0) continue
        drawCourTree(sx.toFloat(), groundScreenY + 6f, index)
    }
}

/** Deux couches de volcans en parallaxe puis fissures incandescentes au
 *  sol, portage du corps de drawVolcanoBackground() côté web (thème jour
 *  uniquement, pas de lune/cendres animées — voir android/README.md). */
private fun DrawScope.drawVolcanoDecor(cameraX: Double, screenWidth: Double, groundScreenY: Float, screenHeight: Float) {
    VolcanoDecor.LAYERS.forEachIndexed { layerIndex, layer ->
        val body = if (layerIndex == 0) Color(0xFF5D1C12) else Color(0xFF71271A)
        val glow = if (layerIndex == 0) Color(0xFFFF9640).copy(alpha = 0.75f) else Color(0xFFFFAA46).copy(alpha = 0.85f)
        for (index in VolcanoDecor.visibleVolcanoIndices(layer, cameraX, screenWidth)) {
            val sx = VolcanoDecor.volcanoScreenX(layer, index, cameraX)
            if (sx < -layer.width || sx > screenWidth + layer.width) continue
            val seed = VolcanoDecor.volcanoSeed(index, layerIndex)
            val vw = VolcanoDecor.volcanoWidth(layer, seed)
            val vh = VolcanoDecor.volcanoHeight(layer, seed)
            drawVolcanoShape(sx.toFloat(), groundScreenY + 4f, vw.toFloat(), vh.toFloat(), body, glow)
        }
    }
    for (index in VolcanoDecor.visibleCrackIndices(cameraX, screenWidth)) {
        val sx = VolcanoDecor.crackWorldX(index) - cameraX
        if (sx < -60.0 || sx > screenWidth + 60.0) continue
        val cy = groundScreenY + 16f + CourDecor.seededRand(index.toDouble()).toFloat() * (screenHeight - groundScreenY - 30f)
        drawCrack(sx.toFloat(), cy)
    }
}

/** Sable/dunes/coquillages puis les accessoires décoratifs (parasol/
 *  serviette/château), portage du corps de drawBeachBackground() côté web
 *  (pas de mer/vagues animées — voir android/README.md). */
private fun DrawScope.drawBeachDecor(cameraX: Double, screenWidth: Double, groundScreenY: Float) {
    for (index in BeachDecor.visibleDuneIndices(cameraX, screenWidth)) {
        val sx = BeachDecor.duneScreenX(index, cameraX)
        val dw = BeachDecor.duneWidth(index)
        if (sx < -dw || sx > screenWidth + dw) continue
        drawDune(sx.toFloat(), groundScreenY, dw.toFloat(), BeachDecor.duneHeight(index).toFloat())
    }
    for (index in BeachDecor.visibleSandBumpIndices(cameraX, screenWidth)) {
        val worldPos = BeachDecor.sandBumpWorldX(index)
        val sx = worldPos - cameraX
        if (sx < -140.0 || sx > screenWidth + 140.0) continue
        if (BeachDecor.hasShell(index)) {
            drawCircle(color = Color(0xFFFFF3E0), radius = 3f, center = Offset(sx.toFloat() + 34f, groundScreenY + 34f))
        }
    }
    for (prop in beachDecorProps) {
        val sx = prop.worldX - cameraX
        if (sx < -160.0 || sx > screenWidth + 160.0) continue
        drawBeachProp(prop.type, sx.toFloat(), groundScreenY + prop.depth.toFloat(), prop.scale.toFloat())
    }
}

/** Générée une seule fois (déterministe, voir BeachDecor.decorativeProps) :
 *  pas besoin de la recalculer à chaque frame. */
private val beachDecorProps by lazy { BeachDecor.decorativeProps() }

private fun DrawScope.drawVolcanoShape(sx: Float, baseY: Float, vw: Float, vh: Float, body: Color, glow: Color) {
    val path = Path().apply {
        moveTo(sx - vw / 2f, baseY)
        lineTo(sx - vw * 0.13f, baseY - vh)
        lineTo(sx + vw * 0.13f, baseY - vh)
        lineTo(sx + vw / 2f, baseY)
        close()
    }
    drawPath(path, color = body)
    drawRect(color = glow, topLeft = Offset(sx - vw * 0.13f, baseY - vh - 3f), size = Size(vw * 0.26f, 7f))
}

private fun DrawScope.drawCrack(sx: Float, cy: Float) {
    val path = Path().apply {
        moveTo(sx - 26f, cy)
        lineTo(sx - 6f, cy + 5f)
        lineTo(sx + 12f, cy - 4f)
        lineTo(sx + 32f, cy + 3f)
    }
    drawPath(path, color = Color(0xFFFF7828).copy(alpha = 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
}

private fun DrawScope.drawDune(sx: Float, groundScreenY: Float, dw: Float, dh: Float) {
    val path = Path().apply {
        moveTo(sx - dw / 2f, groundScreenY + 2f)
        quadraticTo(sx, groundScreenY - dh, sx + dw / 2f, groundScreenY + 2f)
        close()
    }
    drawPath(path, color = Color(0xFFE0C173))
}

private fun DrawScope.drawBeachProp(type: BeachPropType, sx: Float, baseY: Float, scale: Float) {
    when (type) {
        BeachPropType.PARASOL -> {
            val r = 28f * scale
            drawLine(color = Color(0xFF6B4A2F), start = Offset(sx, baseY), end = Offset(sx, baseY - r * 1.4f), strokeWidth = 3f)
            val canopy = Path().apply {
                moveTo(sx - r, baseY - r * 1.3f)
                quadraticTo(sx, baseY - r * 2.1f, sx + r, baseY - r * 1.3f)
                close()
            }
            drawPath(canopy, color = Color(0xFFE74C3C))
        }
        BeachPropType.TOWEL -> drawRoundRect(
            color = Color(0xFF5CA9E0),
            topLeft = Offset(sx - 20f * scale, baseY - 4f * scale),
            size = Size(40f * scale, 10f * scale),
            cornerRadius = CornerRadius(2f * scale, 2f * scale),
        )
        BeachPropType.CASTLE -> {
            val path = Path().apply {
                moveTo(sx - 16f * scale, baseY)
                lineTo(sx - 10f * scale, baseY - 18f * scale)
                lineTo(sx + 10f * scale, baseY - 18f * scale)
                lineTo(sx + 16f * scale, baseY)
                close()
            }
            drawPath(path, color = Color(0xFFCFA958))
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

/** Ce qui s'est passé au fil d'un lancer plage, voir [animateBeachFlight]. */
data class BeachFlightOutcome(val parasolBounced: Boolean, val towelFound: Boolean, val castleCrushed: Boolean)

/**
 * Anime un lancer dans le monde Plage : comme [animateFlight], mais gère en
 * plus le rebond sur un parasol (voir [Beach], `:core`) — si l'atterrissage
 * tombe sur un parasol tiré au sort pour ce lancer, le vol continue avec une
 * nouvelle vitesse au lieu de s'arrêter, jusqu'à un atterrissage "normal"
 * (éventuellement sur une serviette/un château, purement narratifs).
 * `onLanded` reçoit l'état final ET ce qui s'est déclenché pendant le vol.
 */
@Composable
fun animateBeachFlight(result: ThrowResult, onLanded: (FlightState, BeachFlightOutcome) -> Unit): FlightState {
    var state by remember(result) { mutableStateOf(result.toInitialFlightState()) }
    val rotSpeed = remember(result) { rotationSpeed(result.initialSpeed) }

    LaunchedEffect(result) {
        // Tiré une seule fois pour tout le lancer, comme beachOnLaunch() côté
        // web (qui prédit la distance AVANT tout rebond pour ce tirage).
        val events = Beach.rollEvents(result.distanceMeters)
        var used = BeachEvents()
        var parasolBounced = false
        var towelFound = false
        var castleCrushed = false

        var lastFrameMillis = System.currentTimeMillis()
        while (true) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            state = FlightSimulator.step(state, result.effectiveGravity, rotSpeed, dt)
            if (state.hasLanded) {
                when (Beach.landingOutcome(events, used, inSpaceMode = false)) {
                    BeachLandingOutcome.PARASOL_BOUNCE -> {
                        used = used.copy(parasol = true)
                        parasolBounced = true
                        state = Beach.applyParasolBounce(state)
                    }
                    BeachLandingOutcome.TOWEL_FOUND -> {
                        used = used.copy(towel = true)
                        towelFound = true
                        break
                    }
                    BeachLandingOutcome.CASTLE_CRUSHED -> {
                        used = used.copy(castle = true)
                        castleCrushed = true
                        break
                    }
                    BeachLandingOutcome.NORMAL -> break
                }
            }
        }
        onLanded(state, BeachFlightOutcome(parasolBounced, towelFound, castleCrushed))
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
