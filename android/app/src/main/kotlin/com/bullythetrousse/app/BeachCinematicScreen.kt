package com.bullythetrousse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.BeachCinePhase
import com.bullythetrousse.core.BeachCineState
import com.bullythetrousse.core.BeachCinematic
import com.bullythetrousse.core.AimLaunchResult
import com.bullythetrousse.core.BeachPropType
import com.bullythetrousse.core.PhysicsConstants
import com.bullythetrousse.core.QualityProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.min
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
 * Les boîtes de dialogue (`bcShowDialogs()`) figent vraiment la cinématique
 * jusqu'à un tap par réplique, et la phase de visée (AIM) accepte un tir
 * manuel plus tôt que les 5 s automatiques — refusé si la barre oscillante
 * est dans la "zone verte" interdite (voir [BeachCinematic.advanceDialog]/
 * [BeachCinematic.tryLaunchFromAim], `:core`, testés). Seule la
 * course-poursuite de crabes reste purement scénarisée, comme côté web (le
 * joueur n'y a aucune action à faire, `bcUpdate()` ne lit aucune entrée
 * pendant "chase").
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
    // "Pas dans la zone verte !" : rejet transitoire d'un tir manuel pendant
    // AIM, affiché un instant puis effacé tout seul (voir showToast() côté
    // web, ici sans file d'attente puisqu'un seul message à la fois suffit).
    var aimRejectedAt by remember { mutableStateOf<Long?>(null) }

    // Effets d'ambiance (voir CineEffects.kt).
    val profile = LocalGraphicsQuality.current.profile
    val particles = remember(profile) { ParticleField(profile) }
    var viewWidth by remember { mutableFloatStateOf(0f) }
    var viewHeight by remember { mutableFloatStateOf(0f) }

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
                    BeachCinePhase.FORGOT -> CineMessage("bcineForgot", fade = 1.5f)
                    BeachCinePhase.CHEH -> CineMessage("bcineCheh", fade = 0f)
                    BeachCinePhase.AFTER_CHEH -> null
                    BeachCinePhase.FLY1 -> CineMessage("bcineUnlocked", fade = 0.8f)
                    BeachCinePhase.OUTRO -> CineMessage("bcineWelcome", fade = 1.2f)
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
                    // Le château explose en blocs de sable ; le parasol renvoie
                    // la trousse dans une gerbe claire.
                    BeachCinePhase.CASTLE ->
                        if (viewWidth > 0f) {
                            particles.burst(
                                x = viewWidth * 0.42f, y = viewHeight * 0.68f + FOREGROUND_DEPTH,
                                count = 32, spread = 260f, up = 240f, radius = 7f, life = 1.4f,
                                color = Color(0xFFD8B06A), gravity = 700f, drag = 0.5f,
                                jitter = 40f, shrink = false, spin = 5f,
                            )
                        }
                    BeachCinePhase.PARASOL ->
                        if (viewWidth > 0f) {
                            particles.burst(
                                x = viewWidth * 0.42f, y = viewHeight * 0.68f + FOREGROUND_DEPTH - 96f,
                                count = 18, spread = 220f, up = 200f, radius = 4f, life = 0.9f,
                                color = Color(0xFFFFF3C4), gravity = 520f, drag = 0.8f, glow = true,
                            )
                        }
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
            // Sable soulevé, gerbes d'impact et pot d'échappement du bus.
            if (viewWidth > 0f) {
                val w = viewWidth
                val h = viewHeight
                val sandY = h * 0.68f + FOREGROUND_DEPTH
                val trousseX = w * 0.42f
                when (next.phase) {
                    BeachCinePhase.ARRIVE, BeachCinePhase.BUS_LEAVE ->
                        // Le bus fume en manœuvrant.
                        if (Random.nextFloat() < dtf * 14f) {
                            particles.burst(
                                x = trousseX - 260f, y = h * 0.68f + 10f, count = 1,
                                spread = 24f, up = 40f, radius = 9f, life = 1.5f,
                                color = Color(0xFF9A9186), gravity = -22f, drag = 1.1f,
                            )
                        }
                    BeachCinePhase.CRAB ->
                        // Le crabe fait gicler le sable en sortant.
                        if (t < 0.5f && Random.nextFloat() < dtf * 26f) {
                            particles.burst(
                                x = trousseX - 150f, y = sandY + 20f, count = 1,
                                spread = 70f, up = 130f, radius = 3.5f, life = 0.8f,
                                color = Color(0xFFE8CE95), gravity = 520f, drag = 0.7f,
                            )
                        }
                    BeachCinePhase.CHASE, BeachCinePhase.BACK_AWAY ->
                        // La trousse soulève du sable en reculant.
                        if (Random.nextFloat() < dtf * 22f) {
                            particles.burst(
                                x = trousseX, y = sandY + 16f, count = 1,
                                spread = 60f, up = 90f, radius = 3f, life = 0.7f,
                                color = Color(0xFFE8CE95), gravity = 500f, drag = 0.8f,
                            )
                        }
                    else -> Unit
                }
            }
            particles.update(dtf)

            state = next
        }
        onFinished()
    }

    val phase = state.phase

    // Efface le rejet "pas dans la zone verte" après une seconde, sans
    // bloquer d'autres tirs entre-temps.
    LaunchedEffect(aimRejectedAt) {
        val at = aimRejectedAt ?: return@LaunchedEffect
        delay(1000)
        if (aimRejectedAt == at) aimRejectedAt = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                viewWidth = it.width.toFloat()
                viewHeight = it.height.toFloat()
            }
            // bcTap() côté web : un tap fait avancer un dialogue, ou tire
            // plus tôt pendant la visée (refusé si la barre est dans la
            // zone verte). Sans effet dans toutes les autres phases.
            .pointerInput(phase) {
                detectTapGestures {
                    when (phase) {
                        BeachCinePhase.DIALOG -> state = BeachCinematic.advanceDialog(state)
                        BeachCinePhase.AIM -> when (val result = BeachCinematic.tryLaunchFromAim(state)) {
                            is AimLaunchResult.Launched -> state = result.state
                            is AimLaunchResult.Forbidden -> aimRejectedAt = System.currentTimeMillis()
                        }
                        else -> Unit
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (phase != BeachCinePhase.BLACK) {
                if (phase in YARD_PHASES) {
                    drawBeachCineYard(
                        profile = profile,
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
                        profile = profile,
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
                drawParticles(particles)
                // Étalonnage : lumière dorée de bord de mer, puis vignetage.
                drawLightWash(Color(0xFFFFC46B), 0.10f)
                if (profile.vignette) drawVignette(0.34f)
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
                            tr(msg.key),
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

        // #bcine-aim + #meter-wrap : "VISE !!!" et la barre oscillante,
        // tapable pour tirer plus tôt (voir tryLaunchFromAim, :core).
        if (phase == BeachCinePhase.AIM) {
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 96.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tr("bcineAim"),
                    color = Color(0xFFFF3B30),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Text(
                    if (aimRejectedAt != null) tr("bcineNotGreen") else tr("app.tapToShoot"),
                    color = if (aimRejectedAt != null) Color(0xFFFF3B30) else TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                AimMeter(phaseElapsed = state.phaseElapsed)
            }
        }

        // #bcine-dialog : la boîte de dialogue du site, tapée pour avancer.
        if (phase == BeachCinePhase.DIALOG) {
            val lines = state.dialogNextPhase?.let { dialogueLines(it) }.orEmpty()
            val shown = (lines.size - state.dialogQueueRemaining).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            val text = lines.getOrNull(shown) ?: ""
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
                    Text(tr(text), color = TextColor, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Text(
                        tr("bcineContinue"),
                        color = TextDim,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * `#meter-wrap`/`#meter-fill`/`#meter-marker` pendant la phase AIM : la
 * barre oscillante (`bcAimValue = sin(t * 3.4)`, voir [BeachCinematic.aimValue])
 * avec son repère central, dans le même habillage que le [Meter] du jeu
 * normal (voir GameScreen.kt).
 */
@Composable
private fun AimMeter(phaseElapsed: Double) {
    val value = BeachCinematic.aimValue(phaseElapsed).toFloat() // -1..1
    val forbidden = value <= BeachCinematic.AIM_FORBIDDEN_MAX.toFloat()
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(0.8f)
            .height(26.dp)
            .clip(shape)
            .background(Color(0x8C0A0C14))
            .border(2.dp, if (forbidden) Color(0xFFFF3B30) else Color(0xFF222633), shape),
    ) {
        // Zone verte interdite : le tout début de la barre (valeur ≤ -0.6).
        val forbiddenFraction = ((BeachCinematic.AIM_FORBIDDEN_MAX.toFloat() + 1f) / 2f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(forbiddenFraction)
                .background(Color(0xFF3DDC84).copy(alpha = 0.35f)),
        )
        // Repère central.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(6.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.35f)),
        )
        // Curseur, de -1 (gauche) à +1 (droite).
        val markerFraction = ((value + 1f) / 2f).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth(markerFraction)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color.White),
                )
            }
        }
    }
}

/** Un crabe de la séquence : position monde et sortie du sable (0 → 1). */
private class Crab(var x: Float, var rise: Float)

/** Un accessoire posé sur le sable pendant un vol (parasol, château, serviette). */
private class CineProp(val type: BeachPropType, val worldX: Float, var collapsed: Boolean = false)

/** Message blanc en surimpression : `fade` = durée du fondu d'entrée. */
/** Un message de la cinématique : la clé de traduction (`bcMsg.key` côté site),
 *  traduite à l'affichage puisqu'elle est posée depuis la boucle d'animation. */
private class CineMessage(val key: String, val fade: Float) {
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


/** Les répliques du site, affichées pendant les phases sans action. */
/**
 * `bcShowDialogs()` côté web : les répliques d'une pause dialogue, dans
 * l'ordre. Indexées par la phase où la cinématique REPREND après le
 * dialogue ([BeachCineState.dialogNextPhase]) plutôt que par celle qui l'a
 * déclenché — c'est la seule information que l'état conserve, PARASOL/
 * CASTLE/TOWEL eux-mêmes ne survivant pas à l'entrée dans DIALOG.
 */
private fun dialogueLines(nextPhase: BeachCinePhase): List<String> = when (nextPhase) {
    // Clés de traduction, dans l'ordre de bcShowDialogs().
    BeachCinePhase.FLY2 -> listOf("bcineParasol1", "bcineParasol2")
    BeachCinePhase.FLY3 -> listOf("bcineTutoAnyway")
    BeachCinePhase.FLY4 -> listOf("bcineTowel1", "bcineTowel2", "bcineTowel3")
    else -> emptyList()
}

/**
 * `bcDrawYard()` : la cour d'école, la trousse au premier plan et le bus
 * derrière elle. Le dézoom est obtenu en dessinant le décor sur un cadre
 * virtuel plus grand puis en le réduisant, pour ne jamais laisser de bord.
 */
private fun DrawScope.drawBeachCineYard(
    profile: QualityProfile,
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
            val rolling = phase == BeachCinePhase.BUS_IN || phase == BeachCinePhase.BUS_OUT
            drawBus(
                x = busX,
                groundY = vh * 0.68f,
                door = door,
                passenger = onBus,
                sprite = sprite,
                skinId = skinId,
                skinFilter = skinFilter,
                filterQuality = profile.spriteFilter,
                wheelAngle = busX / 22f, // périmètre de la roue : l'angle suit la distance
                bounce = if (rolling) sin(clock * 26f) * 1.6f else 0f,
            )
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
            if (profile.ambientEffects) {
                drawGroundShadow(
                    centerX = tx,
                    groundY = fy + trousseSize * 0.4f,
                    objectSize = trousseSize,
                    height = 0f,
                )
            }
            drawTrousseSprite(sprite, skinId, tx, ty, trousseSize, lean, skinFilter, alpha, profile.spriteFilter)
        }
    }
}

/** `bcDrawBeach()` : le monde Plage, le bus qui dépose puis repart, les
 *  crabes qui sortent du sable, les accessoires et la trousse. */
private fun DrawScope.drawBeachCineBeach(
    profile: QualityProfile,
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

        // Soleil bas sur l'horizon, ses reflets sur l'eau, et trois mouettes :
        // le site n'a rien de tout ça, et c'est ce qui fait « bord de mer ».
        // Coupé en qualité basse : c'est du décor pur.
        if (profile.ambientEffects) {
            drawSunGlow(
                centerX = vw * 0.74f,
                centerY = groundY - vh * 0.42f,
                radius = 30f,
                core = Color(0xFFFFF8E2),
                halo = Color(0xFFFFD98A),
            )
            drawSeaSparkle(
                topY = groundY - vh * 0.10f,
                bottomY = groundY,
                clock = clock,
                alpha = 0.55f,
            )
            for (i in 0 until 3) {
                val seed = i * 0.37f
                val gx = mod((clock * (16f + i * 7f) + seed * vw).toDouble(), (vw + 160f).toDouble()).toFloat() - 80f
                val gy = vh * (0.14f + i * 0.06f) + sin(clock * 0.9f + i) * 7f
                drawGull(
                    centerX = gx,
                    centerY = gy,
                    scale = 0.85f + i * 0.2f,
                    flap = 0.45f + 0.55f * abs(sin(clock * 3.1f + i * 1.3f)),
                    color = Color(0xFF3B4658).copy(alpha = 0.55f),
                )
            }
        }

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
                filterQuality = profile.spriteFilter,
                wheelAngle = busX / 22f,
                bounce = if (phase == BeachCinePhase.BUS_LEAVE) sin(clock * 26f) * 1.6f else 0f,
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
                if (profile.ambientEffects) {
                    drawGroundShadow(
                        centerX = tx,
                        groundY = fy + TROUSSE_SIZE * 0.4f,
                        objectSize = TROUSSE_SIZE,
                        height = max(0f, fy - ty),
                    )
                }
                drawTrousseSprite(sprite, skinId, tx, ty, TROUSSE_SIZE, lean, skinFilter, alpha, profile.spriteFilter)
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
    filterQuality: FilterQuality,
    // Ajouts par rapport au site : les roues tournent et la caisse tressaute
    // tant que le bus roule — sans ça il glisse comme un décor découpé.
    wheelAngle: Float = 0f,
    bounce: Float = 0f,
) {
    val bw = 420f
    val bh = 150f
    val top = groundY - bh + bounce

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
                drawTrousseSprite(sprite, skinId, wx + 26f, top + 62f, 48f, 0f, skinFilter, 1f, filterQuality)
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
    // Roues, avec des rayons qui tournent.
    for (wx in listOf(x + 88f, x + bw - 82f)) {
        val cy = groundY - 14f
        drawCircle(color = Color(0xFF22242C), radius = 22f, center = Offset(wx, cy))
        drawCircle(color = Color(0xFF9AA0AB), radius = 9f, center = Offset(wx, cy))
        for (spoke in 0 until 4) {
            val a = wheelAngle + spoke * (PI.toFloat() / 4f)
            drawLine(
                color = Color(0xFF6E7480),
                start = Offset(wx + cos(a) * 5f, cy + sin(a) * 5f),
                end = Offset(wx + cos(a) * 19f, cy + sin(a) * 19f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
        }
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
