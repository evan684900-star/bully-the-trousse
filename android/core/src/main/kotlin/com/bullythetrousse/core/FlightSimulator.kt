package com.bullythetrousse.core

/**
 * État de la trousse en plein vol : coordonnées "monde" en pixels (avant
 * conversion écran, voir [Camera]), portées depuis les variables
 * worldX/worldY/vx/vy/rotation de gameLoop() côté web.
 */
data class FlightState(
    val worldX: Double,
    val worldY: Double,
    val vx: Double,
    val vy: Double,
    val rotation: Double = 0.0,
) {
    /** true dès que la trousse est repassée sous le sol en redescendant,
     *  exactement la condition d'atterrissage de gameLoop() côté web. */
    val hasLanded: Boolean get() = worldY <= 0 && vy < 0
}

/**
 * Point de départ d'un vol tout juste lancé : au ras du sol (worldY=0), la
 * vitesse initiale du ThrowResult calculé par [ThrowPhysics.simulateThrow].
 */
fun ThrowResult.toInitialFlightState(): FlightState {
    val angleRadians = angleDegrees * Math.PI / 180.0
    return FlightState(
        worldX = 0.0,
        worldY = 0.0,
        vx = initialSpeed * kotlin.math.cos(angleRadians),
        vy = initialSpeed * kotlin.math.sin(angleRadians),
        rotation = 0.0,
    )
}

/**
 * Vitesse de rotation de la trousse pendant le vol (purement visuel),
 * portée depuis "rotSpeed = 6 + v0 / 55" côté web (lockAccuracyAndLaunch).
 */
fun rotationSpeed(initialSpeed: Double): Double = 6.0 + initialSpeed / 55.0

/**
 * Intégration physique du vol, méthode d'Euler semi-implicite : la position
 * avance avec la vitesse verticale AVANT que la gravité ne la freine cette
 * frame-là (même ordre que gameLoop() côté web : "worldX += vx*dt; worldY
 * += vy*dt; vy -= g*dt" — inverser l'ordre donnerait une trajectoire
 * légèrement différente).
 */
object FlightSimulator {
    fun step(state: FlightState, gravity: Double, rotationSpeed: Double, dt: Double): FlightState {
        val newWorldX = state.worldX + state.vx * dt
        val newWorldY = state.worldY + state.vy * dt
        val newVy = state.vy - gravity * dt
        val newRotation = state.rotation + rotationSpeed * dt
        return state.copy(worldX = newWorldX, worldY = newWorldY, vy = newVy, rotation = newRotation)
    }
}
