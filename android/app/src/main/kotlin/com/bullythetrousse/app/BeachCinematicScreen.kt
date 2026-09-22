package com.bullythetrousse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.BeachCinePhase
import com.bullythetrousse.core.BeachCineState
import com.bullythetrousse.core.BeachCinematic
import com.bullythetrousse.core.BeachPropType
import com.bullythetrousse.core.PhysicsConstants
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Cinématique de déblocage de la plage. Le séquencement (30 phases, durées
 * exactes de `BCINE`) vient de [BeachCinematic] (`:core`, testé) ; ce
 * fichier en fait le rendu, porté de `bcUpdate()`/`bcRender()`/
 * `bcDrawYard()`/`bcDrawBeach()` côté web : le bus jaune qui s'arrête
 * devant la trousse, l'embarquement, la fermeture en diaphragme, l'arrivée
 * sur la plage, les crabes qui sortent du sable, la course-poursuite et les
 * vols jusqu'au message de bienvenue.
 *
 * Simplification assumée, déjà documentée côté `:core` : les mini-séquences
 * interactives du web (boîtes de dialogue à valider, visée à ne pas poser
 * dans la zone verte) ne changent jamais l'issue — la séquence se déroule
 * donc toute seule, à la même cadence.
 */
@Composable
fun BeachCinematicScreen(equippedSkin: String, onFinished: () -> Unit) {
    var state by remember { mutableStateOf(BeachCineState()) }

    // État purement visuel, décalque des variables `bc*` côté web.
    var lean by remember { mutableFloatStateOf(0f) }
    var altitude by remember { mutableFloatStateOf(0f) }
    var drop by remember { mutableFloatStateOf(0f) }
    var scroll by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var bang by remember { mutableFloatStateOf(0f) }
    var iris by remember { mutableFloatStateOf(1f) }
    var door by remember { mutableFloatStateOf(0f) }
    var onBus by remember { mutableStateOf(false) }
    var clock by remember { mutableFloatStateOf(0f) }
    val crabs = remember { mutableListOf<Crab>() }
    val props = remember { mutableListOf<CineProp>() }
    var message by remember { mutableStateOf<CineMessage?>(null) }

    val sprite = rememberTrousseSprite()
    val skinFilter = rememberSkinColorFilter(equippedSkin)

    LaunchedEffect(Unit) {
        var lastFrameMillis = System.currentTimeMillis()
        var previousPhase = state.phase
        while (!state.finished) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(40)) / 1000.0
            lastFrameMillis = now
            val dtf = dt.toFloat()
            clock += dtf

            val next = BeachCinematic.step(state, dt)
            val t = next.phaseElapsed.toFloat()

            // bcSetPhase() : ce que l'entrée dans une phase met en place.
            if (next.phase != previousPhase) {
                message = when (next.phase) {
                    BeachCinePhase.FORGOT -> CineMessage("Attends... elle a oublié son maillot.", fade = 1.5f)
                    BeachCinePhase.CHEH -> CineMessage("cheh", fade = 0f)
                    BeachCinePhase.AFTER_CHEH -> null
                    BeachCinePhase.FLY1 -> CineMessage("🏖️ Monde Plage débloqué !", fade = 0.8f)
                    BeachCinePhase.OUTRO -> CineMessage("Bref, bienvenue dans le monde Plage.", fade = 1.2f)
                    else -> message
                }
                when (next.phase) {
                    BeachCinePhase.ARRIVE -> onBus = false
                    BeachCinePhase.CRAB -> {
                        crabs.clear()
                        crabs.add(Crab(x = scroll - 150f, rise = 0f))
                    }
                    BeachCinePhase.SWARM -> {
                        val base = crabs.firstOrNull()?.x ?: (scroll - 150f)
                        for (i in 0 until 5) crabs.add(Crab(x = base - 56f - i * 64f, rise = 0f))
                    }
                    // Les accessoires sont posés au DÉBUT du vol qui y mène, à
                    // la distance exacte que la trousse va parcourir : ils
                    // entrent dans le cadre avant l'impact.
                    BeachCinePhase.FLY1 ->
                        props.add(CineProp(BeachPropType.PARASOL, scroll + 700f * BeachCinematic.FLY1.toFloat() + 520f * BeachCinematic.PARASOL.toFloat() * 0.45f))
                    BeachCinePhase.FLY2 ->
                        props.add(CineProp(BeachPropType.CASTLE, scroll + 200f * PhysicsConstants.SCALE.toFloat()))
                    BeachCinePhase.FLY3 ->
                        props.add(CineProp(BeachPropType.TOWEL, scroll + 620f * BeachCinematic.FLY3.toFloat()))
                    BeachCinePhase.SEATED -> onBus = true
                    BeachCinePhase.BUS_OUT -> door = 0f
                    // Fin du sursaut : la trousse est de nouveau droite et posée.
                    BeachCinePhase.BACK_AWAY -> { drop = 0f; lean = 0f; altitude = 0f }
                    else -> Unit
                }
                previousPhase = next.phase
            }
            message?.let { it.elapsed += dtf }

            // bcUpdate() : la part visuelle, phase par phase.
            when (next.phase) {
                BeachCinePhase.HAIL -> {
                    val p = c01(t / BeachCinematic.HAIL.toFloat())
                    lean = -0.42f * sin(p * PI.toFloat() * 2.5f) * (1f - p * 0.3f)
                }
                BeachCinePhase.DOOR_OPEN -> door = c01(t / BeachCinematic.DOOR_OPEN.toFloat())
                BeachCinePhase.IRIS -> iris = 1f - c01(t / BeachCinematic.IRIS.toFloat())
                BeachCinePhase.BLACK -> iris = 0f
                BeachCinePhase.ARRIVE -> iris = c01(t / (BeachCinematic.ARRIVE.toFloat() * 0.45f))
                BeachCinePhase.PIVOT -> {
                    val p = c01(t / BeachCinematic.PIVOT.toFloat())
                    lean = sin(p * PI.toFloat() * 4f) * 0.5f
                }
                BeachCinePhase.COLLAPSE -> {
                    val p = cOut(c01(t / BeachCinematic.COLLAPSE.toFloat()))
                    drop = 20f * p
                    lean = 0.16f * p
                }
                BeachCinePhase.CRAB -> {
                    val p = c01(t / BeachCinematic.CRAB.toFloat())
                    crabs.firstOrNull()?.rise = c01(p * 2.4f)
                    if (p > 0.35f) {
                        bang = c01((p - 0.35f) * 6f)
                        // La trousse se relève en sursaut, petit bond compris.
                        val up = cOut(c01((p - 0.35f) / 0.3f))
                        drop = 20f * (1f - up)
                        lean = 0.16f * (1f - up)
                        altitude = 26f * sin(up * PI.toFloat())
                    }
                }
                BeachCinePhase.BACK_AWAY -> {
                    scroll += 60f * dtf
                    bang = max(0f, bang - dtf * 0.5f)
                }
                BeachCinePhase.SWARM -> {
                    scroll += 40f * dtf
                    crabs.forEach {
                        it.rise = min(1f, it.rise + dtf * 1.6f)
                        it.x += 70f * dtf
                    }
                }
                BeachCinePhase.CHASE -> {
                    scroll += 340f * dtf
                    crabs.forEach { it.x += 320f * dtf }
                    bang = max(0f, bang - dtf)
                }
                BeachCinePhase.AIM_INTRO -> {
                    scroll += 340f * dtf * (1f - c01(t / BeachCinematic.AIM_INTRO.toFloat())) // freinage net
                    crabs.forEach { it.x += 150f * dtf }
                }
                BeachCinePhase.FLY1 -> {
                    scroll += 700f * dtf
                    altitude = 190f
                    zoom = if (t < 1f) {
                        1f + 0.45f * cOut(t)
                    } else {
                        1.45f - 0.45f * cInOut(c01((t - 1f) / (BeachCinematic.FLY1.toFloat() - 1f)))
                    }
                }
                BeachCinePhase.PARASOL -> {
                    zoom = 1f
                    scroll += 520f * dtf
                    val p = c01(t / BeachCinematic.PARASOL.toFloat())
                    // Descente sur le parasol puis rebond immédiat.
                    altitude = if (p < 0.45f) 190f * (1f - p / 0.45f) + 96f else 96f + 260f * cOut((p - 0.45f) / 0.55f)
                }
                BeachCinePhase.FLY2 -> {
                    val p = c01(t / BeachCinematic.FLY2.toFloat())
                    scroll += (200f * PhysicsConstants.SCALE.toFloat() / BeachCinematic.FLY2.toFloat()) * dtf
                    altitude = 356f + 120f * sin(p * PI.toFloat()) - 356f * cIn(p)
                }
                BeachCinePhase.CASTLE -> {
                    altitude = 0f
                    // Le château s'écroule dès le premier contact.
                    props.forEach {
                        if (it.type == BeachPropType.CASTLE && abs(it.worldX - scroll) < 180f) it.collapsed = true
                    }
                }
                BeachCinePhase.FLY3 -> {
                    val p = c01(t / BeachCinematic.FLY3.toFloat())
                    scroll += 620f * dtf
                    altitude = 300f * sin(p * PI.toFloat() * 0.92f)
                }
                BeachCinePhase.TOWEL -> {
                    altitude = 0f
                    drop = 16f * cOut(c01(t / (BeachCinematic.TOWEL.toFloat() * 0.6f)))
                }
                BeachCinePhase.FLY4 -> {
                    drop = 0f
                    scroll += 620f * dtf
                    altitude = 330f * cOut(c01(t / BeachCinematic.FLY4.toFloat()))
                }
                BeachCinePhase.OUTRO -> {
                    scroll += 560f * dtf
                    altitude = 330f
                }
                else -> Unit
            }
            state = next
        }
        onFinished()
    }

    val phase = state.phase
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (phase != BeachCinePhase.BLACK) {
                if (phase in YARD_PHASES) {
                    drawBeachCineYard(
                        phase = phase,
                        phaseElapsed = state.phaseElapsed,
                        lean = lean,
                        door = door,
                        onBus = onBus,
                        clock = clock,
                        sprite = sprite,
                        skinId = equippedSkin,
                        skinFilter = skinFilter,
                    )
                } else {
                    drawBeachCineBeach(
                        phase = phase,
                        phaseElapsed = state.phaseElapsed,
                        scroll = scroll,
                        zoom = zoom,
                        lean = lean,
                        altitude = altitude,
                        drop = drop,
                        bang = bang,
                        onBus = onBus,
                        crabs = crabs,
                        props = props,
                        clock = clock,
                        sprite = sprite,
                        skinId = equippedSkin,
                        skinFilter = skinFilter,
                    )
                }
            } else {
                drawRect(color = Color.Black)
            }

            // Fermeture / ouverture en diaphragme : un disque transparent au
            // centre d'un voile noir (ctx.arc en sens anti-horaire côté web).
            if (iris < 0.999f) {
                val maxR = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() / 2f
                val hole = Path().apply {
                    addOval(
                        Rect(
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = maxR * cInOut(c01(iris)),
                        ),
                    )
                }
                val veil = Path().apply { addRect(Rect(Offset.Zero, size)) }
                drawPath(Path().apply { op(veil, hole, PathOperation.Difference) }, color = Color.Black)
            }
        }

        // Message blanc en surimpression, à 17 % du haut (bcMsg côté web).
        message?.let { msg ->
            if (phase != BeachCinePhase.BLACK) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.fillMaxHeight(0.22f), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            msg.text,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .alpha(if (msg.fade > 0f) c01(msg.elapsed / msg.fade) else 1f),
                        )
                    }
                }
            }
        }

        // #bcine-aim : "VISE !!!" pendant la phase de visée.
        if (phase == BeachCinePhase.AIM) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "VISE !!!",
                    color = Color(0xFFFF3B30),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
        }

        // #bcine-dialog : la boîte de dialogue du site, pour les temps morts.
        val dialogue = dialogueFor(phase)
        if (dialogue != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PanelBg)
                        .border(2.dp, PanelBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dialogue, color = TextColor, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Un crabe de la séquence : position monde et sortie du sable (0 → 1). */
private class Crab(var x: Float, var rise: Float)

/** Un accessoire posé sur le sable pendant un vol (parasol, château, serviette). */
private class CineProp(val type: BeachPropType, val worldX: Float, var collapsed: Boolean = false)

/** Message blanc en surimpression : `fade` = durée du fondu d'entrée. */
private class CineMessage(val text: String, val fade: Float) {
    var elapsed: Float = 0f
}

/** Les phases qui se jouent encore dans la cour d'école (`yardPhases` côté web). */
private val YARD_PHASES = setOf(
    BeachCinePhase.FADE, BeachCinePhase.HAIL, BeachCinePhase.BUS_IN, BeachCinePhase.DOOR_OPEN,
    BeachCinePhase.BOARDING, BeachCinePhase.SEATED, BeachCinePhase.BUS_OUT, BeachCinePhase.IRIS,
)

// Constantes de cadrage, reprises telles quelles de BCINE_* côté web.
private const val FOREGROUND_DEPTH = 74f // px sous la ligne d'horizon
private const val TROUSSE_SIZE = 96f // plus grande qu'en jeu : elle est au premier plan
private const val DOOR_OFFSET = 127f // centre de la porte depuis le coin avant-gauche du bus

private fun cIn(t: Float) = t * t * t
private fun cOut(t: Float) = 1f - (1f - t).pow(3)
private fun cInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
private fun c01(v: Float) = v.coerceIn(0f, 1f)

/** Les répliques du site, affichées pendant les phases sans action. */
private fun dialogueFor(phase: BeachCinePhase): String? = when (phase) {
    BeachCinePhase.PARASOL -> "Aïe. Un parasol."
    BeachCinePhase.CASTLE -> "Bon. De toute façon le tuto arrive."
    BeachCinePhase.TOWEL -> "Une serviette. Enfin un peu de confort."
    else -> null
}

/**
 * `bcDrawYard()` : la cour d'école, la trousse au premier plan et le bus
 * derrière elle. Le dézoom est obtenu en dessinant le décor sur un cadre
 * virtuel plus grand puis en le réduisant, pour ne jamais laisser de bord.
 */
private fun DrawScope.drawBeachCineYard(
    phase: BeachCinePhase,
    phaseElapsed: Double,
    lean: Float,
    door: Float,
    onBus: Boolean,
    clock: Float,
    sprite: ImageBitmap,
    skinId: String,
    skinFilter: ColorFilter?,
) {
    val w = size.width
    val h = size.height
    val groundY = h * 0.68f

    val zoom = when (phase) {
        BeachCinePhase.BUS_IN -> 1f - 0.42f * cInOut(c01((phaseElapsed / BeachCinematic.BUS_IN).toFloat()))
        BeachCinePhase.FADE, BeachCinePhase.HAIL -> 1f
        else -> 0.58f
    }
    val vw = w / zoom
    val vh = h / zoom
    val fx = w * 0.45f
    val fy = groundY + FOREGROUND_DEPTH
    val fxv = fx / zoom // le même X, vu du décor réduit

    val busTarget = fxv - DOOR_OFFSET
    val busStart = -560f
    val busX = when (phase) {
        BeachCinePhase.BUS_IN -> busStart + (busTarget - busStart) * cOut(c01((phaseElapsed / BeachCinematic.BUS_IN).toFloat()))
        BeachCinePhase.DOOR_OPEN, BeachCinePhase.BOARDING, BeachCinePhase.SEATED -> busTarget
        BeachCinePhase.BUS_OUT -> busTarget + cIn(c01((phaseElapsed / BeachCinematic.BUS_OUT).toFloat())) * (vw + 800f)
        else -> busTarget
    }

    scale(zoom, pivot = Offset.Zero) {
        drawWorldBackdrop(
            world = "cour",
            cameraX = -fxv.toDouble(),
            groundScreenY = vh * 0.68f,
            timeSeconds = clock,
            width = vw,
            height = vh,
        )
        if (phase != BeachCinePhase.FADE && phase != BeachCinePhase.HAIL) {
            drawBus(busX, vh * 0.68f, door, onBus, sprite, skinId, skinFilter)
        }
    }

    // La trousse, au premier plan (hors zoom), tant qu'elle n'est pas montée.
    if (!onBus) {
        var tx = fx
        var ty = fy
        var trousseSize = TROUSSE_SIZE
        var alpha = 1f
        if (phase == BeachCinePhase.BOARDING) {
            val p = cInOut(c01((phaseElapsed / BeachCinematic.BOARDING).toFloat()))
            val doorX = (busX + DOOR_OFFSET) * zoom
            val doorY = groundY - 26f
            tx = fx + (doorX - fx) * p
            ty = fy + (doorY - fy) * p
            trousseSize = TROUSSE_SIZE + (56f - TROUSSE_SIZE) * p
            alpha = 1f - c01((p - 0.6f) / 0.4f)
        }
        if (alpha > 0.01f) {
            drawTrousseSprite(sprite, skinId, tx, ty, trousseSize, lean, skinFilter, alpha)
        }
    }
}

/** `bcDrawBeach()` : le monde Plage, le bus qui dépose puis repart, les
 *  crabes qui sortent du sable, les accessoires et la trousse. */
private fun DrawScope.drawBeachCineBeach(
    phase: BeachCinePhase,
    phaseElapsed: Double,
    scroll: Float,
    zoom: Float,
    lean: Float,
    altitude: Float,
    drop: Float,
    bang: Float,
    onBus: Boolean,
    crabs: List<Crab>,
    props: List<CineProp>,
    clock: Float,
    sprite: ImageBitmap,
    skinId: String,
    skinFilter: ColorFilter?,
) {
    val w = size.width
    val h = size.height
    val vw = w / zoom
    val vh = h / zoom
    val fxv = vw * 0.42f // X de la trousse dans le décor
    val groundY = vh * 0.68f
    val fy = groundY + FOREGROUND_DEPTH

    scale(zoom, pivot = Offset.Zero) {
        drawWorldBackdrop(
            world = "plage",
            cameraX = scroll.toDouble(),
            groundScreenY = groundY,
            timeSeconds = clock,
            width = vw,
            height = vh,
        )

        // Accessoires posés pendant les vols.
        for (prop in props) {
            val sx = prop.worldX - scroll + fxv
            if (sx < -160f || sx > vw + 160f) continue
            if (prop.collapsed) {
                // Château écroulé : un simple tas de sable.
                drawOval(
                    color = Color(0xFFD8B06A),
                    topLeft = Offset(sx - 40f, fy - 14f),
                    size = Size(80f, 22f),
                )
            } else {
                drawBeachProp(prop.type, sx, fy, 1.25f)
            }
        }

        // Le bus dépose la trousse puis repart vers la droite.
        if (phase == BeachCinePhase.ARRIVE || phase == BeachCinePhase.BUS_LEAVE) {
            var busX = fxv - 90f - DOOR_OFFSET
            if (phase == BeachCinePhase.BUS_LEAVE) {
                busX += cIn(c01((phaseElapsed / BeachCinematic.BUS_LEAVE).toFloat())) * (vw + 800f)
            }
            drawBus(
                x = busX,
                groundY = groundY + 20f,
                door = if (phase == BeachCinePhase.ARRIVE) 1f else 0f,
                passenger = onBus,
                sprite = sprite,
                skinId = skinId,
                skinFilter = skinFilter,
            )
        }

        // Crabes : découpés au ras du sable tant qu'ils n'ont pas fini de sortir.
        crabs.forEachIndexed { index, crab ->
            val sx = crab.x - scroll + fxv
            if (sx < -90f || sx > vw + 90f) return@forEachIndexed
            clipRect(left = sx - 80f, top = fy - 160f, right = sx + 80f, bottom = fy + 2f) {
                drawCrab(sx, fy + 34f - 40f * crab.rise, 46f, clock + index)
            }
        }

        if (!onBus) {
            var tx = fxv
            var ty = fy - altitude + drop
            var alpha = 1f
            if (phase == BeachCinePhase.ARRIVE) {
                // Descend le marchepied puis fait un pas de côté sur le sable.
                val p = cOut(c01(((phaseElapsed - BeachCinematic.ARRIVE * 0.3) / (BeachCinematic.ARRIVE * 0.7)).toFloat()))
                tx = (fxv - 90f) + 90f * p
                ty = (groundY - 26f) + (fy - (groundY - 26f)) * p
                alpha = c01((phaseElapsed / (BeachCinematic.ARRIVE * 0.35)).toFloat())
            }
            if (alpha > 0.01f) {
                drawTrousseSprite(sprite, skinId, tx, ty, TROUSSE_SIZE, lean, skinFilter, alpha)
            }
            // Le « ! » de surprise au-dessus de la trousse quand le crabe sort.
            if (bang > 0.01f) drawSurpriseBang(tx, ty - 66f, bang)
        }
    }
}

/**
 * Le « ! » de surprise, tracé au trait plutôt qu'avec une police : la barre
 * puis le point, en doré cerné de brun comme le `strokeText`/`fillText`
 * doré du web.
 */
private fun DrawScope.drawSurpriseBang(x: Float, y: Float, alpha: Float) {
    val gold = Color(0xFFFFCF3F)
    val outline = Color(0xFF281900).copy(alpha = 0.85f)
    val barTop = y - 44f
    val barBottom = y - 14f
    for ((color, width) in listOf(outline to 16f, gold to 9f)) {
        drawLine(
            color = color,
            start = Offset(x, barTop),
            end = Offset(x, barBottom),
            strokeWidth = width,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
    drawCircle(color = outline, radius = 8.5f, center = Offset(x, y), alpha = alpha)
    drawCircle(color = gold, radius = 5f, center = Offset(x, y), alpha = alpha)
}

/** `drawBus()` : le bus scolaire jaune, porte coulissante et passager compris. */
private fun DrawScope.drawBus(
    x: Float,
    groundY: Float,
    door: Float,
    passenger: Boolean,
    sprite: ImageBitmap,
    skinId: String,
    skinFilter: ColorFilter?,
) {
    val bw = 420f
    val bh = 150f
    val top = groundY - bh

    // Ombre au sol.
    drawOval(
        color = Color(0xFF3C2D14).copy(alpha = 0.25f),
        topLeft = Offset(x + bw / 2f - bw * 0.48f, groundY + 4f - 9f),
        size = Size(bw * 0.96f, 18f),
    )
    // Carrosserie.
    val bodyPath = Path().apply {
        moveTo(x, groundY - 14f)
        lineTo(x, top + 26f)
        quadraticBezierTo(x, top, x + 26f, top)
        lineTo(x + bw - 14f, top)
        quadraticBezierTo(x + bw, top, x + bw, top + 16f)
        lineTo(x + bw, groundY - 14f)
        close()
    }
    drawPath(bodyPath, color = Color(0xFFF2B705))
    // Bande décorative.
    drawRect(color = Color(0xFFC98F00), topLeft = Offset(x, groundY - 46f), size = Size(bw, 8f))
    // Pare-brise (à droite : le bus roule vers la droite).
    val windshield = Path().apply {
        moveTo(x + bw - 62f, top + 12f)
        lineTo(x + bw - 8f, top + 16f)
        lineTo(x + bw - 8f, top + 66f)
        lineTo(x + bw - 62f, top + 66f)
        close()
    }
    drawPath(windshield, color = Color(0xFFBFE9F7))
    // Fenêtres latérales.
    for (i in 0 until 4) {
        val wx = x + 28f + i * 64f
        drawRect(color = Color(0xFFBFE9F7), topLeft = Offset(wx, top + 14f), size = Size(52f, 52f))
        drawRect(
            color = Color(0xFF5A4614).copy(alpha = 0.4f),
            topLeft = Offset(wx, top + 14f),
            size = Size(52f, 52f),
            style = Stroke(width = 2f),
        )
        // La trousse assise : son haut dépasse par la 3e fenêtre, que la porte
        // ne recouvre pas.
        if (passenger && i == 2) {
            clipRect(left = wx, top = top + 14f, right = wx + 52f, bottom = top + 66f) {
                drawTrousseSprite(sprite, skinId, wx + 26f, top + 62f, 48f, 0f, skinFilter)
            }
        }
    }
    // Porte : deux battants coulissants, côté gauche du bus.
    val dx = x + 100f
    val dw = 54f
    val dh = 92f
    val doorTop = groundY - dh - 14f
    drawRect(color = Color(0xFF9ED7EA), topLeft = Offset(dx, doorTop), size = Size(dw, dh))
    val slide = (dw / 2f) * c01(door)
    drawRect(color = Color(0xFFE0A904), topLeft = Offset(dx - slide, doorTop), size = Size(dw / 2f, dh))
    drawRect(color = Color(0xFFE0A904), topLeft = Offset(dx + dw / 2f + slide, doorTop), size = Size(dw / 2f, dh))
    drawRect(
        color = Color(0xFF5A4614).copy(alpha = 0.5f),
        topLeft = Offset(dx, doorTop),
        size = Size(dw, dh),
        style = Stroke(width = 2f),
    )
    // Roues.
    for (wx in listOf(x + 88f, x + bw - 82f)) {
        drawCircle(color = Color(0xFF22242C), radius = 22f, center = Offset(wx, groundY - 14f))
        drawCircle(color = Color(0xFF9AA0AB), radius = 9f, center = Offset(wx, groundY - 14f))
    }
}

/** `drawCrab()` : pattes qui frétillent, pinces, corps et yeux sur tiges. */
private fun DrawScope.drawCrab(x: Float, y: Float, crabSize: Float, t: Float) {
    val k = crabSize / 34f
    val wig = sin(t * 9f) * 3f * k
    val shell = Color(0xFFE8503F)
    val limb = Color(0xFFA52A1A)

    for (i in -1..1) {
        for (sgn in listOf(-1f, 1f)) {
            drawLine(
                color = limb,
                start = Offset(x + sgn * 9f * k, y - 2f * k + i * 4f * k),
                end = Offset(x + sgn * (19f * k + i), y + 4f * k + i * 3f * k + wig * (i + 2) * 0.3f),
                strokeWidth = 2.4f * k,
            )
        }
    }
    for (sgn in listOf(-1f, 1f)) {
        drawLine(
            color = limb,
            start = Offset(x + sgn * 11f * k, y - 6f * k),
            end = Offset(x + sgn * 21f * k, y - 13f * k - wig * 0.4f),
            strokeWidth = 2.4f * k,
        )
        drawOval(
            color = shell,
            topLeft = Offset(x + sgn * 24f * k - 6f * k, y - 15f * k - wig * 0.4f - 4.5f * k),
            size = Size(12f * k, 9f * k),
        )
    }
    drawOval(color = shell, topLeft = Offset(x - 14f * k, y - 10f * k), size = Size(28f * k, 20f * k))
    drawOval(
        color = Color.Black.copy(alpha = 0.12f),
        topLeft = Offset(x - 12f * k, y),
        size = Size(24f * k, 8f * k),
    )
    for (sgn in listOf(-1f, 1f)) {
        drawLine(
            color = shell,
            start = Offset(x + sgn * 5f * k, y - 8f * k),
            end = Offset(x + sgn * 5f * k, y - 15f * k),
            strokeWidth = 2f * k,
        )
        drawCircle(color = Color.White, radius = 3.2f * k, center = Offset(x + sgn * 5f * k, y - 17f * k))
        drawCircle(color = Color(0xFF1A1A1A), radius = 1.5f * k, center = Offset(x + sgn * 5f * k, y - 17f * k))
    }
}
