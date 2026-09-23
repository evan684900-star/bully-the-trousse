package com.bullythetrousse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.bullythetrousse.core.SpacePhase
import com.bullythetrousse.core.SpaceSequence
import com.bullythetrousse.core.SpaceState
import com.bullythetrousse.core.Trails
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
fun ThrowCanvas(
    flightState: FlightState?,
    world: String = "cour",
    equippedSkin: String = "classique",
    equippedTrail: String = "blanche",
    /** Non nul pendant le détour en apesanteur : le décor devient spatial et
     *  le QTE se dessine par-dessus (voir [SpaceFlight]). */
    spaceState: SpaceState? = null,
    groundVerticalFraction: Float = 0.68f,
    // `#game-canvas` occupe tout l'écran côté web.
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    // Horloge d'ambiance (nuages, vagues) : tourne en continu tant que le
    // Canvas est affiché, indépendamment de l'état du lancer — sans elle,
    // ces détails resteraient figés tant qu'aucun lancer n'est en cours.
    var animationTimeSeconds by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            withFrameNanos { }
            animationTimeSeconds = (System.currentTimeMillis() - start) / 1000f
        }
    }

    // Positions récentes de la trousse, pour dessiner le sillage derrière
    // elle (trailPoints côté web). Liste simple : elle est relue à chaque
    // frame par le dessin, qui se redéclenche déjà tout seul.
    // Sprite de la trousse + filtre du skin équipé : la trousse en vol est
    // celle que le joueur a équipée, exactement comme paintTrousse() côté web
    // (qui relit `save.equippedSkin` à chaque frame).
    val sprite = rememberTrousseSprite()
    val skinFilter = rememberSkinColorFilter(equippedSkin)
    // Niveau de détail choisi dans les Réglages (voir LocalGraphicsQuality) :
    // il décide de la finesse du sillage.
    val profile = LocalGraphicsQuality.current.profile
    val spriteFilter = spriteFilterQuality()

    val trailPoints = remember { mutableListOf<TrailPoint>() }
    val trailRgb = remember(equippedTrail) { Trails.ALL.firstOrNull { it.id == equippedTrail }?.rgb ?: "255,255,255" }

    Canvas(modifier = modifier) {
        val groundScreenY = size.height * groundVerticalFraction

        // Caméra centrée sur worldX=0 tant qu'il n'y a pas de vol en cours,
        // sinon elle suit la trousse (voir Camera.followX, portage de la
        // logique de gameLoop côté web).
        val cameraX = if (flightState != null) Camera.followX(flightState.worldX, size.width.toDouble()) else 0.0

        // Pendant l'apesanteur, c'est le décor spatial qui remplace le monde —
        // et les nuages de la transition cachent la bascule, comme côté site.
        if (spaceState != null) {
            drawSpaceBackdrop(cameraX, groundScreenY, animationTimeSeconds)
        } else {
            drawWorldBackdrop(world, cameraX, groundScreenY, animationTimeSeconds)
        }

        // Le sillage ne vit que pendant le vol : au repos on repart de zéro,
        // sinon le ruban du lancer précédent resterait accroché à la trousse.
        val nowMillis = System.currentTimeMillis()
        if (flightState == null) {
            trailPoints.clear()
        } else {
            trailPoints.add(TrailPoint(flightState.worldX, flightState.worldY, nowMillis))
            trailPoints.removeAll { nowMillis - it.atMillis > TRAIL_LIFE_MS }
            while (trailPoints.size > profile.trailPoints) trailPoints.removeAt(0)
            drawTrail(
                points = trailPoints,
                cameraX = cameraX,
                groundScreenY = groundScreenY,
                nowMillis = nowMillis,
                color = trailColor(trailRgb, animationTimeSeconds),
                passes = profile.trailPasses,
            )
        }

        val worldY = flightState?.worldY ?: 0.0
        val screen = Camera.worldToScreen(
            worldX = flightState?.worldX ?: 0.0,
            worldY = worldY,
            cameraX = cameraX,
            groundScreenY = groundScreenY.toDouble(),
        )
        drawTrousseSprite(
            image = sprite,
            skinId = equippedSkin,
            centerX = screen.sx.toFloat(),
            centerY = screen.sy.toFloat(),
            size = 56.dp.toPx(),
            rotationRadians = (flightState?.rotation ?: 0.0).toFloat(),
            colorFilter = skinFilter,
            filterQuality = spriteFilter,
        )

        if (spaceState != null) {
            when (spaceState.phase) {
                SpacePhase.TRANSITION -> drawCloudSweep(
                    (spaceState.phaseElapsed / SpaceSequence.TRANSITION_DURATION).toFloat(),
                )
                SpacePhase.QTE -> drawQteRings(
                    currentRadius = SpaceSequence.currentRingRadius(spaceState.phaseElapsed).toFloat(),
                    targetRadius = spaceState.ringTargetRadius.toFloat(),
                )
                else -> Unit
            }
        }
    }
}

/**
 * Le décor complet d'un monde : ciel, sol, nuages et éléments de fond,
 * portage de `drawBackground()`/`drawVolcanoBackground()`/
 * `drawBeachBackground()` côté web. Extrait du corps de [ThrowCanvas] pour
 * que les cinématiques puissent repeindre exactement le même décor (le web
 * les dessine sur le même canvas, via les mêmes fonctions).
 */
internal fun DrawScope.drawWorldBackdrop(
    world: String,
    cameraX: Double,
    groundScreenY: Float,
    timeSeconds: Float,
    // Les cinématiques dessinent le décor sur un cadre VIRTUEL plus grand que
    // l'écran avant de le réduire (voir bcDrawYard() côté web) : sans ces deux
    // paramètres, un dézoom laisserait une bande vide, car `DrawScope.size`
    // ne suit pas les transformations appliquées au canvas.
    width: Float = size.width,
    height: Float = size.height,
) {
    val palette = paletteFor(world)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.skyTop, palette.skyBottom),
            startY = 0f,
            endY = groundScreenY,
        ),
        size = Size(width, groundScreenY),
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.groundTop, palette.groundBottom),
            startY = groundScreenY,
            endY = height,
        ),
        topLeft = Offset(0f, groundScreenY),
        size = Size(width, height - groundScreenY),
    )
    when (world) {
        "volcans" -> {
            drawClouds(cameraX, width.toDouble(), groundScreenY, timeSeconds, tint = Color(0xFFFFD9C2).copy(alpha = 0.5f))
            drawVolcanoDecor(cameraX, width.toDouble(), groundScreenY, height)
        }
        "plage" -> {
            drawClouds(cameraX, width.toDouble(), groundScreenY, timeSeconds, tint = Color.White.copy(alpha = 0.6f))
            drawSea(cameraX, width.toDouble(), groundScreenY, timeSeconds)
            drawBeachDecor(cameraX, width.toDouble(), groundScreenY)
        }
        else -> {
            drawClouds(cameraX, width.toDouble(), groundScreenY, timeSeconds, tint = Color.White.copy(alpha = 0.7f))
            drawCourDecor(cameraX, width.toDouble(), groundScreenY)
        }
    }
}

/**
 * `drawSpaceBackground()` : ciel noir étoilé, planète lointaine et sol
 * lunaire. Les étoiles ont une légère parallaxe, comme le décor des autres
 * mondes.
 */
private fun DrawScope.drawSpaceBackdrop(cameraX: Double, groundScreenY: Float, timeSeconds: Float) {
    val w = size.width
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF05040F), Color(0xFF1B1240)),
            startY = 0f,
            endY = groundScreenY,
        ),
        size = Size(w, groundScreenY),
    )

    val starParallax = -cameraX * 0.05
    for (i in 0 until 90) {
        val sx = mod(i * 137.0 + starParallax, (w + 100f).toDouble()).toFloat() - 50f
        val sy = CourDecor.seededRand(i * 3.1).toFloat() * groundScreenY * 0.9f
        val twinkle = 0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin(timeSeconds * 2f + i))
        drawCircle(
            color = Color.White.copy(alpha = (0.3f + 0.5f * twinkle).coerceIn(0f, 1f)),
            radius = 1f + CourDecor.seededRand(i.toDouble()).toFloat() * 1.5f,
            center = Offset(sx, sy),
        )
    }

    // Planète lointaine, avec son cratère d'ombre.
    val planetX = (w * 0.75f - cameraX * 0.1).toFloat()
    val planetY = groundScreenY * 0.25f
    drawCircle(color = Color(0xFFC97B5F), radius = 46f, center = Offset(planetX, planetY))
    drawCircle(
        color = Color.Black.copy(alpha = 0.15f),
        radius = 12f,
        center = Offset(planetX - 14f, planetY - 10f),
    )

    // Sol lunaire : le même gris que la cour, pour rester cohérent.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF9AA0AB), Color(0xFF6F7480)),
            startY = groundScreenY,
            endY = size.height,
        ),
        topLeft = Offset(0f, groundScreenY),
        size = Size(w, size.height - groundScreenY),
    )

    // Cratères décoratifs, à la place de la craie et des arbres de la cour.
    val spacing = 180.0
    val startIndex = kotlin.math.floor(cameraX / spacing).toInt() - 1
    for (i in startIndex..(startIndex + kotlin.math.ceil(w / spacing).toInt() + 2)) {
        val worldPos = i * spacing + (CourDecor.seededRand(i + 0.4) - 0.5) * 60.0
        val sx = (worldPos - cameraX).toFloat()
        if (sx < -30f || sx > w + 30f) continue
        drawMoonCrater(
            centerX = sx,
            centerY = groundScreenY + 10f + CourDecor.seededRand(i.toDouble()).toFloat() * 14f,
            radius = 10f + CourDecor.seededRand(i + 1.0).toFloat() * 8f,
        )
    }
}

/** Un cratère : une ellipse creusée, plus claire en bas qu'en haut. */
private fun DrawScope.drawMoonCrater(centerX: Float, centerY: Float, radius: Float) {
    drawOval(
        color = Color(0xFF5F646E),
        topLeft = Offset(centerX - radius, centerY - radius * 0.45f),
        size = Size(radius * 2f, radius * 0.9f),
    )
    drawOval(
        color = Color(0xFF878D98),
        topLeft = Offset(centerX - radius * 0.8f, centerY - radius * 0.3f),
        size = Size(radius * 1.6f, radius * 0.6f),
    )
}

/**
 * `drawCloudSweep()` : les nuages qui balaient l'écran de bas en haut
 * pendant la transition, doublés d'un voile plein écran en cloche
 * (0 → 1 → 0). C'est ce voile qui garantit qu'on ne voit JAMAIS le décor
 * basculer, quelle que soit la position des nuages.
 */
private fun DrawScope.drawCloudSweep(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height
    val drift = h * 1.15f - p * h * 1.3f
    for (i in 0 until 5) {
        val cy = drift - i * 150f
        val cx = w * (0.15f + (i % 3) * 0.25f)
        val cloud = Color(0xFFEEF3F8)
        drawCircle(color = cloud, radius = 95f, center = Offset(cx, cy))
        drawCircle(color = cloud, radius = 75f, center = Offset(cx + 75f, cy + 12f))
        drawCircle(color = cloud, radius = 68f, center = Offset(cx - 65f, cy + 18f))
    }
    val coverage = kotlin.math.sin(p * Math.PI).toFloat()
    drawRect(color = Color(0xFFF4F8FF).copy(alpha = coverage.coerceIn(0f, 1f)))
}

/**
 * `drawQteRing()` : l'anneau-cible fixe (vert) et l'anneau blanc qui
 * rétrécit. Il faut taper quand les deux tailles coïncident.
 */
private fun DrawScope.drawQteRings(currentRadius: Float, targetRadius: Float) {
    val center = Offset(size.width / 2f, size.height * 0.4f)
    drawCircle(
        color = Color(0xFF6BFFB0).copy(alpha = 0.9f),
        radius = targetRadius,
        center = center,
        style = Stroke(width = 4f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = max(currentRadius, 2f),
        center = center,
        style = Stroke(width = 5f),
    )
}

/**
 * Un point du sillage : position "monde" et instant où il a été posé. Le
 * ruban se dessine à partir des points encore vivants (voir TRAIL_LIFE).
 */
private data class TrailPoint(val worldX: Double, val worldY: Double, val atMillis: Long)

private const val TRAIL_LIFE_MS = 620L // durée de vie d'un point (TRAIL_LIFE côté web)
private const val TRAIL_HEAD_WIDTH = 13f // largeur près de la trousse (TRAIL_HEAD_W)

/**
 * `drawTrail()` côté web : un ruban fuselé derrière la trousse, large et
 * pâle près de la queue, resserré et lumineux près de la trousse. Deux
 * passes comme le site — une brume diffuse puis un cœur vif — plutôt que
 * les trois du web, dont la passe additive n'a pas d'équivalent direct ici.
 *
 * Sans ça les 15 traînées de la boutique s'achetaient sans jamais rien
 * afficher en vol.
 */
private fun DrawScope.drawTrail(
    points: List<TrailPoint>,
    cameraX: Double,
    groundScreenY: Float,
    nowMillis: Long,
    color: Color,
    // Une seule passe en qualité basse : on garde le cœur du sillage et on
    // laisse tomber la brume, qui coûte un second remplissage plein écran.
    passes: Int = 2,
) {
    if (points.size < 2) return

    val screen = points.map { point ->
        val p = Camera.worldToScreen(point.worldX, point.worldY, cameraX, groundScreenY.toDouble())
        val age = (nowMillis - point.atMillis).toFloat() / TRAIL_LIFE_MS
        Triple(p.sx.toFloat(), p.sy.toFloat(), (1f - age).coerceIn(0f, 1f))
    }

    // Deux passes : brume large et transparente, puis le cœur du sillage.
    val layers = if (passes >= 2) listOf(3.0f to 0.09f, 1.0f to 0.42f) else listOf(1.0f to 0.42f)
    for ((widthMultiplier, alphaMultiplier) in layers) {
        val forward = Path()
        val backward = ArrayList<Offset>(screen.size)
        for (i in screen.indices) {
            val (sx, sy, life) = screen[i]
            val prev = screen[maxOf(0, i - 1)]
            val next = screen[minOf(screen.size - 1, i + 1)]
            val dx = next.first - prev.first
            val dy = next.second - prev.second
            val length = kotlin.math.hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
            // Normale à la trajectoire : c'est elle qui donne l'épaisseur.
            val nx = -dy / length
            val ny = dx / length
            // Profil fuselé : fin en queue, épais près de la trousse, et qui
            // gonfle à mesure que le point pâlit (le sillage se dilue).
            val head = if (screen.size > 1) i.toFloat() / (screen.size - 1) else 1f
            val taper = Math.pow(head.toDouble(), 0.55).toFloat() * (0.2f + 0.8f * minOf(1f, life * 1.4f))
            val halfWidth = maxOf(0.3f, TRAIL_HEAD_WIDTH * 0.5f * widthMultiplier * taper * (1f + (1f - life) * 1.5f))

            val ax = sx + nx * halfWidth
            val ay = sy + ny * halfWidth
            if (i == 0) forward.moveTo(ax, ay) else forward.lineTo(ax, ay)
            backward.add(Offset(sx - nx * halfWidth, sy - ny * halfWidth))
        }
        // Retour par l'autre bord : un seul chemin rempli, donc aucune couture
        // entre les segments (c'est exactement le choix fait côté web).
        for (i in backward.indices.reversed()) {
            forward.lineTo(backward[i].x, backward[i].y)
        }
        forward.close()
        drawPath(forward, color = color.copy(alpha = alphaMultiplier))
    }
}

/** La couleur d'une traînée, depuis son `rgb` ("r,g,b" ou "rainbow"). */
private fun trailColor(rgb: String, timeSeconds: Float): Color {
    if (rgb == "rainbow") {
        // Teinte qui défile, comme trailRgbAt() pour l'arc-en-ciel.
        val hue = (timeSeconds * 120f) % 360f
        return Color.hsv(hue, 0.85f, 1f)
    }
    val parts = rgb.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size == 3) Color(parts[0], parts[1], parts[2]) else Color.White
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

/** Modulo qui reste toujours positif (contrairement à `%` en Kotlin comme en
 *  JS, qui garde le signe du dividende) : utile pour les motifs qui
 *  bouclent en boucle infinie (vagues, nuages). */
internal fun mod(a: Double, n: Double): Double = ((a % n) + n) % n

/**
 * Petits nuages qui dérivent lentement dans le ciel, un détail purement
 * décoratif ajouté au portage (le web n'a pas de nuages sur ces 3 mondes) :
 * dérive constante dans le temps + très légère parallaxe avec la caméra,
 * positions/tailles variées via [CourDecor.seededRand].
 */
private fun DrawScope.drawClouds(cameraX: Double, screenWidth: Double, groundScreenY: Float, timeSeconds: Float, tint: Color) {
    val spacing = 260.0
    val parallax = 0.05
    val driftSpeed = 6.0 // px/s
    val parX = cameraX * parallax + timeSeconds * driftSpeed
    val start = kotlin.math.floor((parX - 90.0) / spacing).toInt()
    val end = kotlin.math.ceil((parX + screenWidth + 90.0) / spacing).toInt()
    for (index in start..end) {
        val sx = index * spacing - parX
        val cy = groundScreenY * (0.12f + CourDecor.seededRand(index.toDouble()).toFloat() * 0.28f)
        val scale = (0.7 + CourDecor.seededRand(index + 1.0) * 0.7).toFloat()
        drawCloud(sx.toFloat(), cy, scale, tint)
    }
}

private fun DrawScope.drawCloud(sx: Float, sy: Float, scale: Float, tint: Color) {
    drawCircle(color = tint, radius = 16f * scale, center = Offset(sx, sy))
    drawCircle(color = tint, radius = 12f * scale, center = Offset(sx - 15f * scale, sy + 4f * scale))
    drawCircle(color = tint, radius = 13f * scale, center = Offset(sx + 15f * scale, sy + 3f * scale))
    drawCircle(color = tint, radius = 9f * scale, center = Offset(sx + 26f * scale, sy + 6f * scale))
}

/**
 * Bande de mer à l'horizon avec des crêtes de vagues qui ondulent, ajoutée
 * au portage (voir "il y a bien une mer côté web, mais figée" —
 * android/README.md) : portage visuel de la mer de `drawBeachBackground()`,
 * avec les crêtes qui ondulent vraiment dans le temps.
 */
private fun DrawScope.drawSea(cameraX: Double, screenWidth: Double, groundScreenY: Float, timeSeconds: Float) {
    val seaHeight = 46f
    val seaTop = groundScreenY - seaHeight
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1F8FC4), Color(0xFF4FC4E0)),
            startY = seaTop,
            endY = groundScreenY,
        ),
        topLeft = Offset(0f, seaTop),
        size = Size(screenWidth.toFloat(), seaHeight),
    )
    val wrapWidth = screenWidth + 160.0
    for (i in 0 until 18) {
        val sx = mod(i * 137.0 + kotlin.math.sin(timeSeconds * 0.7 + i) * 14.0 - cameraX * 0.08, wrapWidth) - 80.0
        val sy = seaTop + 8f + ((i * 37) % (seaHeight - 14).toInt())
        val path = Path().apply {
            moveTo(sx.toFloat(), sy)
            quadraticBezierTo(sx.toFloat() + 9f, sy - 3f, sx.toFloat() + 18f, sy)
        }
        drawPath(path, color = Color.White.copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

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
        quadraticBezierTo(sx, groundScreenY - dh, sx + dw / 2f, groundScreenY + 2f)
        close()
    }
    drawPath(path, color = Color(0xFFE0C173))
}

internal fun DrawScope.drawBeachProp(type: BeachPropType, sx: Float, baseY: Float, scale: Float) {
    when (type) {
        BeachPropType.PARASOL -> {
            val r = 28f * scale
            drawLine(color = Color(0xFF6B4A2F), start = Offset(sx, baseY), end = Offset(sx, baseY - r * 1.4f), strokeWidth = 3f)
            val canopy = Path().apply {
                moveTo(sx - r, baseY - r * 1.3f)
                quadraticBezierTo(sx, baseY - r * 2.1f, sx + r, baseY - r * 1.3f)
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
 * Anime un [FlightState] à partir d'un [ThrowResult] fraîchement lancé,
 * pas à pas via [FlightSimulator], jusqu'à l'atterrissage. `onLanded` est
 * appelé une seule fois, quand `hasLanded` devient vrai.
 *
 * Le détour par l'apesanteur ([SpaceFlight]) s'intercale au sommet de la
 * courbe quand le lancer a déclenché l'easter egg : la trousse se fige, le
 * QTE se joue, puis le vol reprend avec la vitesse du boost — exactement
 * comme le `state = "space_transition"` du site, qui suspend la boucle de
 * vol sans l'abandonner.
 */
@Composable
fun animateFlight(
    result: ThrowResult,
    space: SpaceFlight? = null,
    vampire: VampireBoostController? = null,
    onLanded: () -> Unit,
): FlightState {
    var state by remember(result) { mutableStateOf(result.toInitialFlightState()) }
    val rotSpeed = remember(result) { rotationSpeed(result.initialSpeed) }

    LaunchedEffect(result) {
        var lastFrameMillis = System.currentTimeMillis()
        var spaceDone = space == null
        while (!state.hasLanded) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now

            // Apogée atteinte (`vy <= 0` côté site) : on bascule en apesanteur
            // au lieu de laisser la trousse redescendre.
            if (!spaceDone && space != null && state.vy <= 0.0) {
                spaceDone = true
                val boost = space.run()
                state = state.copy(vx = boost.first, vy = boost.second)
                // Le temps a passé pendant la séquence : repartir de l'horloge
                // courante, sinon le premier pas d'après avalerait toute sa durée.
                lastFrameMillis = System.currentTimeMillis()
                continue
            }

            // Le boost 🦇 accélère vx AVANT le pas de simulation, comme la
            // section "flying" de gameLoop() côté site.
            if (vampire != null) state = state.copy(vx = vampire.step(dt, state.vx))
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
fun animateBeachFlight(
    result: ThrowResult,
    vampire: VampireBoostController? = null,
    onLanded: (FlightState, BeachFlightOutcome) -> Unit,
): FlightState {
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
            if (vampire != null) state = state.copy(vx = vampire.step(dt, state.vx))
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
