package com.bullythetrousse.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.CinePhase
import com.bullythetrousse.core.CourDecor
import com.bullythetrousse.core.QualityProfile
import com.bullythetrousse.core.RockState
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.VolcanoCineOutcome
import com.bullythetrousse.core.VolcanoCineState
import com.bullythetrousse.core.VolcanoCinematic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cinématique de déblocage du volcan. Le déroulement (durées des phases,
 * mini-jeu d'esquive, QTE de clics, issue) vient de [VolcanoCinematic]
 * (`:core`, testé) ; ce fichier n'en fait que le rendu, porté de
 * `cineRender()`/`cineDrawYard()`/`cineDrawSky()`/`cineDrawArrival()`
 * côté web — le zoom sur la cour, les fissures, l'éruption, la traversée
 * des nuages, les roches incandescentes et l'arrivée dans le cratère.
 *
 * Les variables purement visuelles du web (`cineShake`, `cineFlash`,
 * `cineLaneAnim`, `cineWobble`, `cineCloudScroll`, `cineRot`) vivent ici,
 * puisqu'elles n'influencent pas l'issue de la séquence.
 *
 * Non porté : les particules de poussière (`spawnDust`), décoratives.
 */
@Composable
fun VolcanoCinematicScreen(equippedSkin: String, onFinished: (VolcanoCineOutcome) -> Unit) {
    var state by remember { mutableStateOf(VolcanoCineState()) }

    // Pendant état visuel (aucune incidence sur l'issue, cf. KDoc).
    var laneAnim by remember { mutableFloatStateOf(0f) }
    var wobble by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableFloatStateOf(0f) }
    var shake by remember { mutableFloatStateOf(0f) }
    var cloudScroll by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }
    var shakeOffset by remember { mutableStateOf(Offset.Zero) }
    var shakeRotation by remember { mutableFloatStateOf(0f) }

    // Effets d'ambiance (voir CineEffects.kt) : matière en suspension,
    // onde de choc de l'impact et colonne de fumée de l'éruption.
    val profile = LocalGraphicsQuality.current.profile
    val particles = remember(profile) { ParticleField(profile) }
    var smoke by remember { mutableFloatStateOf(0f) }
    var shockwave by remember { mutableFloatStateOf(-1f) }
    var impactDone by remember { mutableStateOf(false) }
    var viewWidth by remember { mutableFloatStateOf(0f) }
    var viewHeight by remember { mutableFloatStateOf(0f) }

    val sprite = rememberTrousseSprite()
    val skinFilter = rememberSkinColorFilter(equippedSkin)
    val sfx = LocalSfx.current
    // Le grondement du tremblement de terre est un VRAI fichier (le seul
    // bruitage du jeu qui en soit un), joué de la phase QUAKE jusqu'à la fin
    // de l'éruption, dont il suit le volume décroissant.
    val quake = rememberQuakeSound()

    LaunchedEffect(Unit) {
        var lastFrameMillis = System.currentTimeMillis()
        var previousPhase = state.phase
        while (state.outcome == null) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            // Même plafond que cineLoop() : une frame perdue ne doit pas
            // téléporter la séquence.
            val dt = ((now - lastFrameMillis).coerceAtMost(40)) / 1000.0
            lastFrameMillis = now
            clock += dt.toFloat()

            val next = VolcanoCinematic.step(state, dt)
            val dtf = dt.toFloat()

            // cineSetPhase() : l'entrée dans certaines phases déclenche un effet.
            if (next.phase != previousPhase) {
                if (next.phase == CinePhase.QUAKE) quake.start()
                if (next.phase == CinePhase.LANDING) sfx.play(SfxCatalog.SPACE)
                if (next.phase == CinePhase.ERUPTION) {
                    sfx.play(SfxCatalog.LAUNCH)
                    sfx.play(SfxCatalog.CRASH)
                    flash = 1f
                    shake = 26f
                    if (viewWidth > 0f) {
                        val fx = viewWidth * 0.45f
                        val groundY = viewHeight * 0.68f
                        // Braises incandescentes...
                        particles.burst(
                            x = fx, y = groundY, count = 54, spread = 400f, up = 700f,
                            radius = 5f, life = 1.7f, color = Color(0xFFFFB03A),
                            gravity = 540f, drag = 0.35f, jitter = 40f, glow = true,
                        )
                        // ...et les blocs de roche arrachés au sol.
                        particles.burst(
                            x = fx, y = groundY, count = 24, spread = 320f, up = 560f,
                            radius = 8f, life = 2.1f, color = Color(0xFF3A2A24),
                            gravity = 760f, drag = 0.2f, jitter = 50f, shrink = false, spin = 7f,
                        )
                        smoke = if (profile.ambientEffects) 1f else 0f
                    }
                }
                previousPhase = next.phase
            }
            // Une roche encaissée fait vaciller la trousse (cineResolveRock).
            if (next.lives < state.lives) {
                wobble = 1f
                shake = 14f
            }
            if (next.phase == CinePhase.DEATH && state.phase != CinePhase.DEATH) {
                shake = 30f
                sfx.play(SfxCatalog.CRASH)
            }

            // Le grondement décroît avec l'éruption puis s'arrête ; en cas de
            // mort il s'éteint en une seconde (`quakeAudio.volume - dt`).
            when (next.phase) {
                CinePhase.ERUPTION -> {
                    val p = (next.phaseElapsed / VolcanoCinematic.ERUPTION_DURATION).coerceIn(0.0, 1.0)
                    quake.setVolume((QuakeSound.MAX_VOLUME * (1.0 - p)).toFloat())
                }
                CinePhase.DEATH -> quake.fadeOut(dt)
                CinePhase.ASCENT, CinePhase.STABILIZE, CinePhase.ROCKS,
                CinePhase.DESCENT, CinePhase.LANDING, CinePhase.OUTRO -> quake.stop()
                else -> Unit
            }

            // cineUpdate() : la part visuelle, phase par phase.
            when (next.phase) {
                CinePhase.APPROACH ->
                    if (next.phaseElapsed > VolcanoCinematic.APPROACH_DURATION - 1.6 && Random.nextFloat() < dtf * 8f) {
                        shake = 1.5f
                    }
                CinePhase.QUAKE -> {
                    val p = (next.phaseElapsed / VolcanoCinematic.QUAKE_DURATION).toFloat()
                    shake = 3f + 27f * p.pow(1.6f)
                }
                CinePhase.ERUPTION -> {
                    val p = (next.phaseElapsed / VolcanoCinematic.ERUPTION_DURATION).toFloat().coerceIn(0f, 1f)
                    shake = 26f * (1f - p)
                    rotation += 0.25f
                }
                CinePhase.ASCENT -> {
                    val p = (next.phaseElapsed / VolcanoCinematic.ASCENT_DURATION).toFloat().coerceIn(0f, 1f)
                    cloudScroll += (1500f * (1f - p).pow(1.6f) + 50f) * dtf
                    rotation += (7f * (1f - p) + 0.5f) * dtf
                }
                CinePhase.STABILIZE -> {
                    cloudScroll += 40f * dtf
                    rotation *= 0.9f
                }
                CinePhase.ROCKS -> cloudScroll += 30f * dtf
                CinePhase.DESCENT -> {
                    cloudScroll -= 620f * dtf
                    rotation += 0.02f
                }
                CinePhase.LANDING -> shake *= 0.02f.pow(dtf)
                CinePhase.DEATH -> {
                    shake *= 0.05f.pow(dtf)
                    fade = (next.phaseElapsed / VolcanoCinematic.DEATH_DURATION).toFloat().coerceIn(0f, 1f)
                }
                else -> Unit
            }

            // Émission continue des particules, selon la phase.
            if (viewWidth > 0f) {
                val w = viewWidth
                val h = viewHeight
                val groundY = h * 0.68f
                when (next.phase) {
                    CinePhase.APPROACH ->
                        // Quelques grains montent déjà du sol : le volcan se réveille.
                        if (next.phaseElapsed > VolcanoCinematic.APPROACH_DURATION - 2.2 &&
                            Random.nextFloat() < dtf * 9f
                        ) {
                            particles.burst(
                                x = Random.nextFloat() * w, y = groundY, count = 1,
                                spread = 14f, up = 26f, radius = 2.5f, life = 1.6f,
                                color = Color(0xFFB39784), gravity = -34f, drag = 0.9f,
                            )
                        }
                    CinePhase.QUAKE -> {
                        val p = (next.phaseElapsed / VolcanoCinematic.QUAKE_DURATION).toFloat()
                        if (Random.nextFloat() < dtf * (10f + 34f * p)) {
                            particles.burst(
                                x = Random.nextFloat() * w, y = groundY + 4f, count = 1,
                                spread = 45f, up = 120f + 220f * p, radius = 3.5f, life = 1.4f,
                                color = Color(0xFFAA8C78), gravity = 380f, drag = 0.7f,
                            )
                        }
                    }
                    CinePhase.ASCENT ->
                        // Traînée de braises derrière la trousse propulsée.
                        if (Random.nextFloat() < dtf * 26f) {
                            particles.burst(
                                x = w / 2f, y = h * 0.62f, count = 1, spread = 60f, up = -120f,
                                radius = 3.5f, life = 0.9f, color = Color(0xFFFF9A3C),
                                gravity = 120f, drag = 1.2f, jitter = 50f, glow = true,
                            )
                        }
                    CinePhase.ROCKS -> {
                        // Étincelles arrachées à la roche en approche.
                        val rp = rockProgress(next.phase, next.rockState, next.rockElapsed)
                        if (rp > 0f && Random.nextFloat() < dtf * 34f) {
                            val ty = h * 0.52f + sin(clock * 10.13f) * 8f
                            val rx = w / 2f + (laneX(next.rockLane.toFloat(), w) - w / 2f) * rp
                            val ry = h * 0.06f + (ty - h * 0.06f) * rp
                            particles.burst(
                                x = rx, y = ry, count = 1, spread = 70f, up = -30f,
                                radius = 3f, life = 0.7f, color = Color(0xFFFFC24D),
                                gravity = 60f, drag = 1.4f, jitter = 18f, glow = true,
                            )
                        }
                    }
                    CinePhase.LANDING -> {
                        val p = (next.phaseElapsed / VolcanoCinematic.LANDING_DURATION).toFloat()
                        if (p >= 0.58f && !impactDone) {
                            impactDone = true
                            shockwave = 0f
                            shake = 16f
                            particles.burst(
                                x = w / 2f, y = groundY, count = 34, spread = 340f, up = 260f,
                                radius = 9f, life = 1.5f, color = Color(0xFFBE7B5A),
                                gravity = 620f, drag = 0.9f, jitter = 30f,
                            )
                            particles.burst(
                                x = w / 2f, y = groundY, count = 14, spread = 240f, up = 380f,
                                radius = 4f, life = 1.2f, color = Color(0xFFFFB03A),
                                gravity = 520f, drag = 0.6f, glow = true,
                            )
                        }
                    }
                    CinePhase.DEATH ->
                        if (state.phase != CinePhase.DEATH) {
                            particles.burst(
                                x = w / 2f, y = h * 0.5f, count = 30, spread = 420f, up = 260f,
                                radius = 7f, life = 1.6f, color = Color(0xFF3A2A24),
                                gravity = 700f, drag = 0.4f, spin = 8f, shrink = false,
                            )
                        }
                    else -> Unit
                }
            }
            particles.update(dtf)
            if (shockwave >= 0f) {
                shockwave += dtf / 0.75f
                if (shockwave > 1f) shockwave = -1f
            }
            smoke = max(0f, smoke - dtf * 0.2f)

            // cineRender() : amortissements communs à toutes les phases.
            laneAnim += (next.lane - laneAnim) * min(1f, dtf * 11f)
            wobble = max(0f, wobble - dtf * 1.4f)
            flash = max(0f, flash - dtf * 1.6f)
            shakeOffset = if (shake > 0.15f) {
                Offset((Random.nextFloat() - 0.5f) * shake, (Random.nextFloat() - 0.5f) * shake)
            } else {
                Offset.Zero
            }
            shakeRotation = if (shake > 0.15f) (Random.nextFloat() - 0.5f) * shake * 0.05f else 0f

            state = next
        }
        onFinished(state.outcome!!)
    }

    val phase = state.phase
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                viewWidth = it.width.toFloat()
                viewHeight = it.height.toFloat()
            }
            // pointerdown : un tap n'importe où compte pour le QTE de la
            // descente ; pendant les roches, c'est le côté touché qui décide
            // du sens de l'esquive (#cine-sides côté web).
            .pointerInput(phase) {
                detectTapGestures { offset ->
                    state = when (phase) {
                        CinePhase.DESCENT -> VolcanoCinematic.click(state)
                        CinePhase.ROCKS ->
                            VolcanoCinematic.move(state, if (offset.x < size.width / 2f) -1 else 1)
                        else -> state
                    }
                }
            }
            // Ajout par rapport au site (pensé pour la souris/le clavier) :
            // un vrai geste de glissement pour esquiver, plus naturel au
            // doigt que de viser une moitié d'écran. `move()` (:core) ignore
            // silencieusement un appel hors de la fenêtre d'esquive, donc ce
            // détecteur et le tap ci-dessus peuvent cohabiter sans jamais se
            // marcher dessus ni déclencher une double esquive.
            .pointerInput(phase) {
                if (phase != CinePhase.ROCKS) return@pointerInput
                val swipeThresholdPx = 40.dp.toPx()
                var dragAccumulated = 0f
                var alreadyDodged = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulated = 0f
                        alreadyDodged = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulated += dragAmount
                        if (!alreadyDodged && kotlin.math.abs(dragAccumulated) > swipeThresholdPx) {
                            alreadyDodged = true
                            state = VolcanoCinematic.move(state, if (dragAccumulated > 0) 1 else -1)
                        }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val amplitude = shake
            // La secousse est appliquée en CSS sur le canvas côté web, avec une
            // légère surtaille pour ne jamais laisser apparaître de bord noir.
            val body: DrawScope.() -> Unit = {
                when (phase) {
                    CinePhase.FADE, CinePhase.APPROACH, CinePhase.QUAKE, CinePhase.ERUPTION ->
                        drawCineYard(phase, state.phaseElapsed, rotation, clock, smoke, profile, sprite, equippedSkin, skinFilter)
                    CinePhase.LANDING, CinePhase.OUTRO ->
                        drawCineArrival(phase, state.phaseElapsed, rotation, clock, profile, sprite, equippedSkin, skinFilter)
                    else ->
                        drawCineSky(
                            phase = phase,
                            phaseElapsed = state.phaseElapsed,
                            laneAnim = laneAnim,
                            wobble = wobble,
                            cloudScroll = cloudScroll,
                            rotation = rotation,
                            clock = clock,
                            rockLane = state.rockLane,
                            rockProgress = rockProgress(phase, state.rockState, state.rockElapsed),
                            rockSeed = state.rockIndex + 1,
                            profile = profile,
                            sprite = sprite,
                            skinId = equippedSkin,
                            skinFilter = skinFilter,
                        )
                }
                // La matière en suspension et l'onde de choc suivent la
                // secousse comme le reste du plan.
                drawParticles(particles)
                if (shockwave >= 0f) {
                    drawShockwave(
                        centerX = size.width / 2f,
                        centerY = size.height * 0.68f,
                        progress = shockwave,
                        maxRadius = size.width * 0.62f,
                        color = Color(0xFFFFD9A8),
                    )
                }
            }
            if (amplitude > 0.15f) {
                translate(shakeOffset.x, shakeOffset.y) {
                    scale(1f + amplitude / 260f) {
                        rotate(shakeRotation) { body() }
                    }
                }
            } else {
                body()
            }

            // Étalonnage du plan : la lumière chaude du volcan, puis le
            // vignetage qui referme le cadre.
            when (phase) {
                CinePhase.QUAKE -> drawLightWash(Color(0xFFFF7A2E), 0.10f)
                CinePhase.ERUPTION -> drawLightWash(Color(0xFFFF7A2E), 0.26f)
                CinePhase.LANDING, CinePhase.OUTRO -> drawLightWash(Color(0xFFFF7A2E), 0.14f)
                CinePhase.ASCENT, CinePhase.STABILIZE, CinePhase.ROCKS, CinePhase.DESCENT ->
                    drawLightWash(Color(0xFF9FD0FF), 0.08f)
                else -> Unit
            }
            if (profile.vignette) drawVignette(0.42f)

            // Éclair de l'explosion, par-dessus tout le reste.
            if (flash > 0.01f) {
                drawRect(color = Color(0xFFFFDCAA).copy(alpha = flash * 0.85f))
            }
            // #cine-fade : voile noir de l'échec.
            if (fade > 0.001f) {
                drawRect(color = Color.Black.copy(alpha = fade))
            }
        }

        // ---- #cine-ui : les surcouches d'interface ----

        // #cine-lives : les cœurs, centrés tout en haut, très espacés.
        if (phase == CinePhase.ROCKS) {
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

        // #cine-warn : le triangle d'alerte qui clignote quand une roche vise
        // la voie de la trousse (top: 17%, 110×96, clignotement de 0,22 s).
        if (phase == CinePhase.ROCKS && state.rockState == RockState.WARN) {
            val blink = rememberInfiniteTransition(label = "warn")
            val warnAlpha by blink.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
                label = "warnBlink",
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.fillMaxHeight(0.17f), contentAlignment = Alignment.BottomCenter) {
                    WarningTriangle(modifier = Modifier.alpha(warnAlpha))
                }
            }
        }

        // #cine-sides : les deux zones de tap (top 32%, bottom 24%, 24% de large).
        if (phase == CinePhase.ROCKS) {
            Box(modifier = Modifier.fillMaxSize()) {
                SideTapZone("◀", Alignment.CenterStart)
                SideTapZone("▶", Alignment.CenterEnd)
            }
        }

        // #cine-clickqte : consigne, jauge et compteur, à 17 % du bas.
        if (phase == CinePhase.DESCENT) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 460.dp)
                    .fillMaxHeight(0.83f)
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Clique vite pour reprendre ton équilibre !",
                    color = Color(0xFFFF4D4D),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(999.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                (state.clicks.toFloat() / VolcanoCinematic.CLICK_TARGET).coerceIn(0f, 1f),
                            )
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFF4D4D), Accent))),
                    )
                }
                Text(
                    "${state.clicks} / ${VolcanoCinematic.CLICK_TARGET}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // Annonce finale, dessinée en texte par-dessus le canvas côté web.
        if (phase == CinePhase.OUTRO) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.fillMaxHeight(0.26f), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        "🌋 Monde Volcan débloqué !",
                        color = Accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha((state.phaseElapsed / 0.6).toFloat().coerceIn(0f, 1f)),
                    )
                }
            }
        }
    }
}

/** `#cine-warn .tri` + `.bang` : un triangle jaune de 110×96 frappé d'un « ! ». */
@Composable
private fun WarningTriangle(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.width(110.dp).height(96.dp)) {
            val triangle = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(triangle, color = Color(0xFFFFCF3F))
        }
        Text(
            "!",
            color = Color(0xFF2A1500),
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 26.dp),
        )
    }
}

/** `#cine-sides .side` : une zone de tap translucide, haute de 32 % à 76 %. */
@Composable
private fun BoxScope.SideTapZone(label: String, alignment: Alignment) {
    Box(
        modifier = Modifier
            .align(alignment)
            .fillMaxWidth(0.24f)
            .fillMaxHeight(0.44f)
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(2.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 40.sp)
    }
}

private fun seeded(seed: Double) = CourDecor.seededRand(seed).toFloat()

/** `cineRockProgress()` : -1 quand aucune roche n'est en vol. */
private fun rockProgress(phase: CinePhase, rockState: RockState, rockElapsed: Double): Float = when {
    phase == CinePhase.DEATH -> 1f
    phase != CinePhase.ROCKS -> -1f
    rockState == RockState.WARN -> 0.42f * c01((rockElapsed / VolcanoCinematic.ROCK_WARN).toFloat())
    rockState == RockState.INCOMING -> 0.42f + 0.58f * c01((rockElapsed / VolcanoCinematic.ROCK_TRAVEL).toFloat())
    else -> -1f
}

/**
 * `cineDrawYard()` : la cour d'école habituelle, caméra qui se rapproche
 * lentement de la trousse puis plonge quand le sol explose.
 */
private fun DrawScope.drawCineYard(
    phase: CinePhase,
    phaseElapsed: Double,
    rotation: Float,
    clock: Float,
    smoke: Float,
    profile: QualityProfile,
    sprite: ImageBitmap,
    skinId: String,
    skinFilter: ColorFilter?,
) {
    val w = size.width
    val h = size.height
    val cameraX = -w * 0.45 // la trousse (worldX = 0) se retrouve à 45 % de l'écran
    val groundY = h * 0.68f
    val fx = w * 0.45f
    val fy = groundY - 26f

    val zoom = when (phase) {
        CinePhase.APPROACH -> 1f + 1.5f * cInOut(c01((phaseElapsed / VolcanoCinematic.APPROACH_DURATION).toFloat()))
        CinePhase.QUAKE -> 2.5f
        CinePhase.ERUPTION -> 2.5f - 1.6f * cOut(c01((phaseElapsed / VolcanoCinematic.ERUPTION_DURATION).toFloat()))
        else -> 1f
    }
    val erupt = if (phase == CinePhase.ERUPTION) {
        c01((phaseElapsed / VolcanoCinematic.ERUPTION_DURATION).toFloat())
    } else {
        0f
    }
    val drop = cIn(erupt) * h * 1.5f // le décor plonge : on s'élève

    // `drop` est multiplié par le zoom : côté web la translation est appliquée
    // APRÈS la mise à l'échelle (ctx.scale puis ctx.translate), donc son effet
    // à l'écran est agrandi d'autant — c'est aussi ce que suppose le calcul de
    // `ty` plus bas.
    translate(left = 0f, top = drop * zoom) {
        scale(zoom, pivot = Offset(fx, fy)) {
            drawWorldBackdrop("cour", cameraX, groundY, clock)
            if (phase == CinePhase.QUAKE || phase == CinePhase.ERUPTION) {
                drawCineCracks(phase, phaseElapsed, groundY)
            }
            if (erupt > 0f) drawCineEruption(fx, groundY, erupt)
        }
    }

    // Colonne de fumée de l'éruption : hors du zoom, elle monte devant le
    // décor mais derrière la trousse.
    if (smoke > 0.01f && profile.ambientEffects) {
        // Même repère que l'ombre portée : le sol zoomé descend de (26 + drop) × zoom.
        drawSmokeColumn(
            centerX = fx,
            baseY = fy + (26f + drop) * zoom,
            height = h * 0.8f,
            clock = clock,
            alpha = smoke,
        )
    }

    // La trousse : posée au sol, puis projetée vers le centre de l'écran.
    var tx = fx
    var ty = fy
    if (erupt > 0f) {
        tx = fx + (w * 0.5f - fx) * cOut(erupt)
        ty = fy + drop * zoom - cOut(erupt) * h * 0.55f
    } else if (phase == CinePhase.QUAKE) {
        ty += sin(clock * 16.7f) * 2f
    }
    val rot = if (phase == CinePhase.QUAKE) sin(clock * 4.5f) * 0.08f else rotation
    val trousseSize = 60f * zoom
    // Ombre portée : elle ancre la trousse au sol tant qu'elle y est, et
    // s'efface à mesure qu'elle est projetée en l'air.
    if (erupt < 0.6f && profile.ambientEffects) {
        // Le sol est zoomé autour de (fx, fy) : la ligne d'horizon, à 26 px
        // sous la trousse au repos, se retrouve donc à 26 × zoom.
        drawGroundShadow(
            centerX = tx,
            groundY = fy + (26f + drop) * zoom,
            objectSize = trousseSize,
            height = max(0f, fy - ty),
        )
    }
    drawTrousseSprite(sprite, skinId, tx, ty, trousseSize, rot, skinFilter, filterQuality = profile.spriteFilter)
}

/** `cineDrawCracks()` : les fissures qui s'ouvrent dans le sol de la cour. */
private fun DrawScope.drawCineCracks(phase: CinePhase, phaseElapsed: Double, groundY: Float) {
    val p = if (phase == CinePhase.QUAKE) c01(((phaseElapsed - 3.0) / 5.0).toFloat()) else 1f
    if (p <= 0f) return
    val w = size.width
    val strokeWidth = 3f + 5f * p
    for (k in 0 until 3) {
        val bx = w * (0.25f + k * 0.25f)
        val path = Path().apply {
            moveTo(bx, groundY + 2f)
            for (i in 1..5) lineTo(bx + (seeded(k * 9.0 + i) - 0.5f) * 60f * p, groundY + 2f + i * 14f)
        }
        drawPath(path, color = Color(0xFF1E1412).copy(alpha = 0.5f * p), style = Stroke(width = strokeWidth))
    }
    // Lueur de lave dans la fissure centrale.
    val lava = Path().apply {
        moveTo(w * 0.5f, groundY + 2f)
        for (i in 1..5) lineTo(w * 0.5f + (seeded(i.toDouble()) - 0.5f) * 50f * p, groundY + 2f + i * 14f)
    }
    drawPath(lava, color = Color(0xFFFF6E1E).copy(alpha = 0.55f * p), style = Stroke(width = 2f * p))
}

/** `cineDrawEruption()` : la boule incandescente et ses éjectas. */
private fun DrawScope.drawCineEruption(fx: Float, groundY: Float, p: Float) {
    val r = 30f + 420f * cOut(p)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFFFFF0BE).copy(alpha = 1f - p),
                0.35f to Color(0xFFFF9628).copy(alpha = 0.85f * (1f - p)),
                1f to Color(0xFF781E0A).copy(alpha = 0f),
            ),
            center = Offset(fx, groundY),
            radius = r,
        ),
        radius = r,
        center = Offset(fx, groundY),
    )
    for (i in 0 until 14) {
        val a = -PI.toFloat() / 2f + (seeded(i.toDouble()) - 0.5f) * 2.2f
        val d = r * (0.4f + seeded(i + 3.0) * 0.8f)
        drawCircle(
            color = Color(0xFF3C2823).copy(alpha = 1f - p),
            radius = 5f + seeded(i + 7.0) * 9f,
            center = Offset(fx + cos(a) * d, groundY + sin(a) * d),
        )
    }
}

/** `cineDrawSky()` : montée, stabilisation, esquive des roches et descente. */
private fun DrawScope.drawCineSky(
    phase: CinePhase,
    phaseElapsed: Double,
    laneAnim: Float,
    wobble: Float,
    cloudScroll: Float,
    rotation: Float,
    clock: Float,
    rockLane: Int,
    rockProgress: Float,
    rockSeed: Int,
    profile: QualityProfile,
    sprite: ImageBitmap,
    skinId: String,
    skinFilter: ColorFilter?,
) {
    val w = size.width
    val h = size.height
    val alt = when (phase) {
        CinePhase.ASCENT -> cOut(c01((phaseElapsed / VolcanoCinematic.ASCENT_DURATION).toFloat()))
        CinePhase.DESCENT -> 1f - 0.3f * c01((phaseElapsed / VolcanoCinematic.DESCENT_DURATION).toFloat())
        else -> 1f
    }

    // Ciel : bleu de cour -> bleu d'altitude.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(
                    red = (126f - 78f * alt) / 255f,
                    green = (200f - 122f * alt) / 255f,
                    blue = (227f - 47f * alt) / 255f,
                ),
                Color(
                    red = (216f - 74f * alt) / 255f,
                    green = (243f - 71f * alt) / 255f,
                    blue = (255f - 21f * alt) / 255f,
                ),
            ),
            startY = 0f,
            endY = h,
        ),
    )

    if (profile.ambientEffects) {
        // Étoiles : visibles seulement une fois très haut, elles disent
        // l'altitude mieux qu'un dégradé.
        drawStars(intensity = c01((alt - 0.55f) / 0.45f) * 0.9f, clock = clock)
        // Le soleil, juste au-dessus de la mer de nuages.
        drawSunGlow(
            centerX = w * 0.78f,
            centerY = h * 0.17f,
            radius = 26f,
            core = Color(0xFFFFF6D8),
            halo = Color(0xFFFFE9A8),
        )
    }

    // Deux couches de nuages : la lointaine défile moins vite et reste pâle,
    // ce qui creuse la profondeur (le site n'en a qu'une). En qualité basse,
    // seule la couche proche est dessinée.
    if (profile.cloudLayers > 1) {
        drawCineCloudField(cloudScroll * 0.35f, count = 10, alphaMul = 0.45f, scaleMul = 1.9f)
    }
    drawCineCloudField(cloudScroll, count = 16, alphaMul = 0.85f)
    if (alt > 0.45f) drawCineCloudSea((alt - 0.45f) / 0.55f, clock)

    // Lignes de vitesse : la montée est violente, la descente rapide.
    val speed = when (phase) {
        CinePhase.ASCENT -> 1f - c01((phaseElapsed / VolcanoCinematic.ASCENT_DURATION).toFloat()) * 0.4f
        CinePhase.DESCENT -> 0.85f
        else -> 0f
    }
    if (profile.ambientEffects) drawSpeedLines(scroll = cloudScroll, intensity = speed)

    // Trousse
    val bob = sin(clock * 10.13f) * 8f
    val ty = when (phase) {
        CinePhase.ASCENT -> h * 1.2f + (h * 0.52f - h * 1.2f) * cOut(c01((phaseElapsed / VolcanoCinematic.ASCENT_DURATION).toFloat()))
        CinePhase.DESCENT -> h * 0.5f + bob * 0.5f
        else -> h * 0.52f + bob
    }
    val wob = wobble * sin(clock * 149.6f) * 16f
    val tx = laneX(laneAnim, w) + wob
    drawTrousseSprite(
        image = sprite,
        skinId = skinId,
        centerX = tx,
        centerY = ty,
        size = 70f,
        rotationRadians = rotation + wobble * 0.25f * sin(clock * 104.7f),
        colorFilter = skinFilter,
        filterQuality = profile.spriteFilter,
    )

    // Roche volcanique en approche : elle part du haut de l'écran et fond sur
    // la voie visée.
    if (rockProgress >= 0f) {
        val rx = w / 2f + (laneX(rockLane.toFloat(), w) - w / 2f) * rockProgress
        val ry = h * 0.06f + (ty - h * 0.06f) * rockProgress
        drawCineRock(
            x = rx,
            y = ry,
            r = 8f + 60f * rockProgress * rockProgress,
            seed = rockSeed,
            clock = clock,
            fromX = w / 2f,
            fromY = h * 0.06f,
        )
    }
}

/** `cineLaneX()` : les trois voies, écartées de 20 % de la largeur. */
private fun laneX(lane: Float, w: Float) = w / 2f + lane * w * 0.2f

private fun DrawScope.drawCineRock(
    x: Float,
    y: Float,
    r: Float,
    seed: Int,
    clock: Float,
    fromX: Float,
    fromY: Float,
) {
    // Traînée de feu : un fuseau qui remonte vers le point de départ de la
    // roche. Ajout par rapport au site, où la roche arrive sans sillage.
    val dx = x - fromX
    val dy = y - fromY
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (len > 1f) {
        val ux = dx / len
        val uy = dy / len
        val tail = min(len, r * 6f)
        val halfWidth = r * 0.62f
        val trail = Path().apply {
            moveTo(x - uy * halfWidth, y + ux * halfWidth)
            lineTo(x + uy * halfWidth, y - ux * halfWidth)
            lineTo(x - ux * tail, y - uy * tail)
            close()
        }
        drawPath(
            path = trail,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFFC24D).copy(alpha = 0.55f),
                    1f to Color(0xFFFF6E1E).copy(alpha = 0f),
                ),
                start = Offset(x, y),
                end = Offset(x - ux * tail, y - uy * tail),
            ),
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFFFF6E1E).copy(alpha = 0.35f),
                1f to Color(0xFFFF6E1E).copy(alpha = 0f),
            ),
            center = Offset(x, y),
            radius = r * 2f,
        ),
        radius = r * 2f,
        center = Offset(x, y),
    )
    rotate(degrees = (seed * 1.7f + clock * 1.11f) * 180f / PI.toFloat(), pivot = Offset(x, y)) {
        val body = Path()
        for (i in 0 until 9) {
            val a = (i / 9f) * 2f * PI.toFloat()
            val rad = r * (0.72f + seeded(seed * 10.0 + i) * 0.4f)
            val px = x + cos(a) * rad
            val py = y + sin(a) * rad
            if (i == 0) body.moveTo(px, py) else body.lineTo(px, py)
        }
        body.close()
        drawPath(body, color = Color(0xFF2F221F))
        val streak = Path().apply {
            moveTo(x - r * 0.45f, y - r * 0.1f)
            lineTo(x - r * 0.05f, y + r * 0.18f)
            lineTo(x + r * 0.4f, y - r * 0.28f)
        }
        drawPath(
            streak,
            color = Color(0xFFFF963C).copy(alpha = 0.9f),
            style = Stroke(width = max(1f, r * 0.09f)),
        )
    }
}

private fun DrawScope.drawCineCloudField(scroll: Float, count: Int, alphaMul: Float, scaleMul: Float = 1f) {
    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val r = (55f + seeded(i * 7.7) * 95f) * scaleMul
        val x = seeded(i * 3.3) * (w + 320f) - 160f
        val y = mod(
            (seeded(i * 5.1) * (h + 700f) + scroll * (0.7f + seeded(i.toDouble()) * 0.6f)).toDouble(),
            (h + 700f).toDouble(),
        ).toFloat() - 300f
        drawCinePuff(x, y, r, 0.35f + 0.4f * seeded(i * 2.2) * alphaMul)
    }
}

private fun DrawScope.drawCinePuff(x: Float, y: Float, r: Float, alpha: Float) {
    val color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
    drawCircle(color = color, radius = r, center = Offset(x, y))
    drawCircle(color = color, radius = r * 0.72f, center = Offset(x + r * 0.75f, y + r * 0.15f))
    drawCircle(color = color, radius = r * 0.66f, center = Offset(x - r * 0.7f, y + r * 0.2f))
}

/** `cineDrawCloudSea()` : la mer de nuages qui défile sous nos pieds. */
private fun DrawScope.drawCineCloudSea(k: Float, clock: Float) {
    val w = size.width
    val h = size.height
    val base = h * (1.15f - 0.3f * k)
    val drift = clock * 11.1f
    val alpha = min(1f, k * 1.4f)
    for (i in 0 until 12) {
        val x = mod((i * 190f - drift).toDouble(), (w + 380f).toDouble()).toFloat() - 190f
        drawCinePuff(x, base - 60f - seeded(i.toDouble()) * 40f, 90f + seeded(i * 1.3) * 50f, 0.9f * alpha)
    }
    // Le site pose un aplat blanc net ; un dégradé sur les 60 premiers pixels
    // donne une vraie masse cotonneuse au lieu d'une bande de peinture.
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0f),
                0.55f to Color.White.copy(alpha = 0.86f * alpha),
                1f to Color(0xFFDDE9F5).copy(alpha = 0.95f * alpha),
            ),
            startY = base - 70f,
            endY = h,
        ),
        topLeft = Offset(0f, base - 70f),
        size = Size(w, h - base + 80f),
    )
}

/** `cineDrawArrival()` : le sol volcanique surgit, la trousse tombe dans un cratère. */
private fun DrawScope.drawCineArrival(
    phase: CinePhase,
    phaseElapsed: Double,
    rotation: Float,
    clock: Float,
    profile: QualityProfile,
    sprite: ImageBitmap,
    skinId: String,
    skinFilter: ColorFilter?,
) {
    val w = size.width
    val h = size.height
    val p = if (phase == CinePhase.LANDING) {
        c01((phaseElapsed / VolcanoCinematic.LANDING_DURATION).toFloat())
    } else {
        1f
    }
    val impact = 0.58f
    val rise = if (phase == CinePhase.LANDING) (1f - cOut(min(1f, p / impact))) * h * 0.85f else 0f

    drawRect(color = Color(0xFF8D2415))
    translate(left = 0f, top = rise) {
        drawWorldBackdrop("volcans", cameraX = 0.0, groundScreenY = h * 0.68f, timeSeconds = clock)
    }

    val groundY = h * 0.68f + rise
    val cx = w * 0.5f
    if (p >= impact) {
        // Petit cratère d'atterrissage.
        drawOval(
            color = Color(0xFF140805).copy(alpha = 0.55f),
            topLeft = Offset(cx - 54f, groundY + 8f - 16f),
            size = Size(108f, 32f),
        )
    }
    val ty = if (p < impact) {
        h * 0.05f + (groundY - 26f - h * 0.05f) * cIn(p / impact)
    } else {
        groundY - 22f
    }
    if (p >= impact) {
        // Lueur de lave au fond du cratère, sous la trousse.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFF7A2E).copy(alpha = 0.55f),
                    1f to Color(0xFFFF7A2E).copy(alpha = 0f),
                ),
                center = Offset(cx, groundY + 6f),
                radius = 92f,
            ),
            radius = 92f,
            center = Offset(cx, groundY + 6f),
        )
    }
    if (profile.ambientEffects) {
        drawGroundShadow(centerX = cx, groundY = groundY + 6f, objectSize = 70f, height = max(0f, groundY - 22f - ty))
    }
    drawTrousseSprite(sprite, skinId, cx, ty, 70f, rotation, skinFilter, filterQuality = profile.spriteFilter)
}
