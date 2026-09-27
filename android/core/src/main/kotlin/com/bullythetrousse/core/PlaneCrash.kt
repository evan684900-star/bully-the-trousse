package com.bullythetrousse.core

import kotlin.math.cos
import kotlin.math.exp

/**
 * Le crash sur l'avion de ligne, portage de `startPlaneCrash()`/
 * `updatePlaneCrash()` (index.html, section 9quater) : c'est la porte
 * d'entrée du monde Ville. Sur la Plage, un lancer parfait qui arrive juste
 * avant le sommet de sa trajectoire finit encastré sur le flanc d'un avion
 * qui le double par la gauche ; l'avion reprend de l'altitude, puis des
 * nuages balaient l'écran et la cinématique d'arrivée prend le relais.
 *
 * `px`/`py` sont la position "monde" de l'avion (même repère que la
 * trousse : x vers la droite, y vers le haut depuis le sol).
 */
data class PlaneCrashState(
    val t: Double = 0.0,
    val attached: Boolean = false,
    val impactT: Double = 0.0,
    val px: Double,
    val py: Double,
    /** Instant où les nuages commencent à balayer l'écran, null avant. */
    val sweepStart: Double? = null,
    /** Avancement du balayage (0 → 1, dépasse 1 à la fin). */
    val sweep: Double = 0.0,
    /** La main est passée à la cinématique d'arrivée. */
    val handed: Boolean = false,
    /** Amplitude de la secousse de l'avion (purement visuelle). */
    val shake: Double = 0.0,
    val flight: FlightState,
    val rotationSpeed: Double,
    /** Vitesse de l'avion (px/s), fixée au départ : voir [PlaneCrash.start]. */
    val speed: Double = PlaneCrash.PLANE_SPEED,
)

object PlaneCrash {
    /** px/s : l'avion double la trousse par la gauche. */
    const val PLANE_SPEED = 1500.0
    const val PLANE_SCALE = 0.9

    /** La trousse s'encastre un peu en avant du centre de l'avion. */
    const val STICK_X = 70.0

    /**
     * Une trousse très puissante file plus vite que [PLANE_SPEED] : l'avion
     * accélère pour la rattraper en ~0,7 s quoi qu'il arrive, et elle ne peut
     * pas passer sous le sable en l'attendant (sinon « ??? » restait affiché
     * pour toujours, sans jamais arriver en Ville).
     */
    const val CATCH_SECONDS = 0.7
    const val MIN_ALTITUDE = 40.0

    /** Secondes entre l'impact et le début du balayage des nuages. */
    const val SWEEP_DELAY = 3.0
    const val SWEEP_DURATION = 0.75

    /**
     * La condition exacte du site : sur la Plage, lancer parfait, pas en
     * apesanteur, encore en montée mais presque au sommet (vy < 170) et assez
     * haut (worldY > 40).
     */
    fun shouldStart(world: String, isPerfect: Boolean, inSpaceMode: Boolean, flight: FlightState): Boolean =
        world == "plage" && isPerfect && !inSpaceMode && flight.vy > 0 && flight.vy < 170 && flight.worldY > 40

    /** L'avion part de derrière l'écran, à gauche, à la hauteur de la trousse. */
    fun start(flight: FlightState, rotationSpeed: Double, screenWidth: Double): PlaneCrashState {
        val startGap = screenWidth * 0.3 + 520
        return PlaneCrashState(
            px = flight.worldX - startGap,
            py = flight.worldY + 4,
            flight = flight,
            rotationSpeed = rotationSpeed,
            speed = maxOf(PLANE_SPEED, flight.vx + startGap / CATCH_SECONDS),
        )
    }

    /** Ce qu'une image a déclenché, pour les bruitages et la suite. */
    data class Step(val state: PlaneCrashState, val impact: Boolean, val handOver: Boolean)

    fun step(state: PlaneCrashState, dt: Double, gravity: Double): Step {
        val t = state.t + dt
        val shake = maxOf(0.0, state.shake - dt * 30)
        if (!state.attached) {
            val f = state.flight
            var flight = f.copy(
                worldX = f.worldX + f.vx * dt,
                worldY = f.worldY + f.vy * dt,
                vy = f.vy - gravity * dt,
                rotation = f.rotation + state.rotationSpeed * dt,
            )
            if (flight.worldY < MIN_ALTITUDE) flight = flight.copy(worldY = MIN_ALTITUDE, vy = maxOf(flight.vy, 0.0))
            val px = state.px + state.speed * dt
            val py = state.py + (flight.worldY + 4 - state.py) * minOf(1.0, dt * 4)
            val impact = px + STICK_X * PLANE_SCALE >= flight.worldX
            val next = state.copy(
                t = t,
                px = px,
                py = py,
                shake = if (impact) 14.0 else shake,
                attached = impact,
                impactT = if (impact) t else state.impactT,
                flight = if (impact) flight.copy(rotation = 0.35) else flight,
            )
            return Step(next, impact = impact, handOver = false)
        }
        val since = t - state.impactT
        val px = state.px + state.speed * dt
        // L'avion reprend de l'altitude, plus franchement au début.
        val py = state.py + (if (since < 2.5) 110 else 40) * dt
        val flight = state.flight.copy(
            worldX = px + STICK_X * PLANE_SCALE,
            worldY = py - 2,
            rotation = -0.1 + 0.45 * exp(-since * 3) * cos(since * 18),
        )
        val sweepStart = state.sweepStart ?: if (since >= SWEEP_DELAY) t else null
        val sweep = if (sweepStart != null) (t - sweepStart) / SWEEP_DURATION else 0.0
        val handOver = sweepStart != null && sweep >= 1 && !state.handed
        return Step(
            state.copy(
                t = t, px = px, py = py, shake = shake, flight = flight,
                sweepStart = sweepStart, sweep = sweep, handed = state.handed || handOver,
            ),
            impact = false,
            handOver = handOver,
        )
    }
}
