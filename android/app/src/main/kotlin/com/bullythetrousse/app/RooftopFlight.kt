package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.bullythetrousse.core.Beep
import com.bullythetrousse.core.FlightSimulator
import com.bullythetrousse.core.FlightState
import com.bullythetrousse.core.PlaneCrash
import com.bullythetrousse.core.PlaneCrashState
import com.bullythetrousse.core.Puddle
import com.bullythetrousse.core.ThrowResult
import com.bullythetrousse.core.VilleEventType
import com.bullythetrousse.core.VilleEvents
import com.bullythetrousse.core.VilleRooftop
import com.bullythetrousse.core.Waveform
import com.bullythetrousse.core.rotationSpeed
import com.bullythetrousse.core.toInitialFlightState
import kotlin.random.Random

/** Où en est un lancer depuis le toit de la Ville. */
enum class RooftopPhase { FLYING, SLIDING, AIMING }

/**
 * Le pilote d'un lancer depuis le toit de la Ville (section 9quater
 * d'index.html) : une flaque au point de chute fait glisser la trousse 3 s,
 * puis une barre de visée tremblante permet de la relancer plus vite —
 * autant de fois qu'elle retombe dans une flaque. Les règles sont dans
 * [VilleRooftop] (`:core`, testé) ; cette classe tient l'état observable par
 * l'écran (la barre, la consigne) et reçoit le tap de relance.
 */
class RooftopFlight(
    val perfectWindow: Double,
    wasPerfect: Boolean,
    /** Retirée à chaque lancer (`villePuddleSeed`) : les flaques changent de place. */
    val puddleSeed: Double = Random.nextDouble() * 1000,
) {
    var phase by mutableStateOf(RooftopPhase.FLYING)
        private set
    var aimStartedAtMillis by mutableLongStateOf(0L)
        private set
    var perfect = wasPerfect
        internal set
    var slides = 0
        private set
    private var relaunchRequested = false

    /** L'évènement du moment : les flaques suivent le calendrier en direct, comme le site. */
    val event: VilleEventType? get() = VilleEvents.activeEvent(System.currentTimeMillis())?.type

    fun puddles(fromX: Double, toX: Double): List<Puddle> = VilleRooftop.puddlesInRange(fromX, toX, event, puddleSeed)

    fun puddleAt(x: Double): Boolean = VilleRooftop.puddleAt(x, event, puddleSeed)

    /** La valeur de la barre de relance à l'instant [nowMillis] (-1 à 1). */
    fun aimValue(nowMillis: Long): Double = VilleRooftop.aimValue((nowMillis - aimStartedAtMillis) / 1000.0)

    /** Un tap sur l'écran : ne sert qu'à relancer depuis la barre. */
    fun tap(): Boolean {
        if (phase != RooftopPhase.AIMING) return false
        relaunchRequested = true
        return true
    }

    internal fun startSlide() {
        phase = RooftopPhase.SLIDING
        slides++
    }

    internal fun startAim(nowMillis: Long) {
        aimStartedAtMillis = nowMillis
        relaunchRequested = false
        phase = RooftopPhase.AIMING
    }

    internal fun consumeRelaunch(): Boolean {
        val requested = relaunchRequested
        relaunchRequested = false
        return requested
    }

    internal fun fly() {
        phase = RooftopPhase.FLYING
    }
}

/** "Splash" de la glissade (deux bips du site). */
private val SPLASH = listOf(Beep(420.0, 0.18, Waveform.SINE, 0.08), Beep(260.0, 0.3, Waveform.TRIANGLE, 0.06))

/**
 * Anime un lancer depuis le toit : le vol, puis — tant qu'elle atterrit
 * dans une flaque — glissade, visée et relance. [onLanded] reçoit l'état
 * final, dont la position donne la VRAIE distance (plus longue que celle du
 * seul premier vol dès qu'il y a eu une flaque).
 */
@Composable
fun animateVilleFlight(
    result: ThrowResult,
    rooftop: RooftopFlight,
    vampire: VampireBoostController? = null,
    onLanded: (FlightState) -> Unit,
): FlightState {
    val sfx = LocalSfx.current
    var state by remember(result) { mutableStateOf(result.toInitialFlightState()) }

    LaunchedEffect(result) {
        var rotSpeed = rotationSpeed(result.initialSpeed)
        var lastLaunchSpeed = result.initialSpeed
        var slideSpeed = 0.0
        var slideTime = 0.0
        var lastFrameMillis = System.currentTimeMillis()
        while (true) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            when (rooftop.phase) {
                RooftopPhase.FLYING -> {
                    if (vampire != null) state = state.copy(vx = vampire.step(dt, state.vx))
                    state = FlightSimulator.step(state, result.effectiveGravity, rotSpeed, dt)
                    if (state.hasLanded) {
                        state = state.copy(worldY = 0.0)
                        if (!rooftop.puddleAt(state.worldX)) break
                        // Fuite d'eau / pluie : la trousse glisse sur une flaque.
                        slideSpeed = VilleRooftop.slideStartSpeed(state.vx)
                        slideTime = 0.0
                        rooftop.startSlide()
                        sfx.play(SPLASH)
                    }
                }
                RooftopPhase.SLIDING -> {
                    slideTime += dt
                    val (x, speed) = VilleRooftop.slideStep(state.worldX, slideSpeed, dt)
                    slideSpeed = speed
                    state = state.copy(worldX = x, rotation = VilleRooftop.slideRotation(state.rotation, dt))
                    if (slideTime >= VilleRooftop.SLIDE_SECONDS) rooftop.startAim(now)
                }
                RooftopPhase.AIMING -> {
                    val elapsed = (now - rooftop.aimStartedAtMillis) / 1000.0
                    // La barre ne bloque jamais : relance automatique au bout de 6 s.
                    if (rooftop.consumeRelaunch() || elapsed >= VilleRooftop.AIM_AUTO_RELAUNCH_SECONDS) {
                        val relaunch = VilleRooftop.relaunch(
                            VilleRooftop.aimValue(elapsed), lastLaunchSpeed, rooftop.perfectWindow, rooftop.perfect,
                        )
                        rooftop.perfect = relaunch.isPerfect
                        lastLaunchSpeed = relaunch.initialSpeed
                        rotSpeed = relaunch.rotationSpeed
                        state = state.copy(vx = relaunch.vx, vy = relaunch.vy, worldY = 0.0)
                        rooftop.fly()
                        sfx.play(com.bullythetrousse.core.SfxCatalog.LAUNCH)
                    }
                }
            }
        }
        onLanded(state)
    }
    return state
}

/**
 * Le crash sur l'avion (voir [PlaneCrash], `:core`) : une fois déclenché
 * par un lancer parfait sur la Plage, il prend le relais de l'animation de
 * vol jusqu'à ce que les nuages couvrent l'écran.
 */
class PlaneCrashFlight {
    var state by mutableStateOf<PlaneCrashState?>(null)
        private set

    /** Joue tout le crash ; [onHandOver] quand les nuages couvrent tout. */
    suspend fun run(start: PlaneCrashState, gravity: Double, onImpact: () -> Unit, onHandOver: () -> Unit) {
        var current = start
        state = current
        var lastFrameMillis = System.currentTimeMillis()
        while (true) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            val step = PlaneCrash.step(current, dt, gravity)
            current = step.state
            state = current
            if (step.impact) onImpact()
            if (step.handOver) {
                onHandOver()
                return
            }
        }
    }
}
