package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class FlightSimulatorTest {
    /**
     * Vérifie que l'intégration image par image (FlightSimulator, portage de
     * la boucle "flying" de gameLoop()) converge vers la même distance que la
     * formule fermée de ThrowPhysics.simulateThrow(), pour un lancer sans
     * rebond ni évènement spécial. dt=1/120 (bien plus fin que les ~1/60
     * d'un vrai écran) pour que l'erreur d'intégration reste négligeable.
     */
    @Test
    fun `l'integration pas a pas converge vers la meme distance que la formule fermee`() {
        val input = ThrowInput(puissanceLevel = 10, vitesseLevel = 5, lockedPower = 0.9, accuracyValue = -0.2)
        val expected = ThrowPhysics.simulateThrow(input)

        var state = expected.toInitialFlightState()
        val rotSpeed = rotationSpeed(expected.initialSpeed)
        // dt bien plus fin qu'à l'écran (~1/60) pour que l'erreur systématique
        // de l'intégration d'Euler (semi-implicite, donc pas juste du bruit)
        // reste petite devant la tolérance ci-dessous.
        val dt = 1.0 / 2000.0

        // On avance jusqu'à dépasser worldY=0 en redescendant (atterrissage),
        // exactement la condition utilisée côté web.
        var steps = 0
        while (!state.hasLanded && steps < 100_000) {
            state = FlightSimulator.step(state, expected.effectiveGravity, rotSpeed, dt)
            steps++
        }

        assertTrue(steps < 100_000, "le vol ne s'est jamais terminé")
        val landedDistanceMeters = state.worldX / PhysicsConstants.SCALE
        assertTrue(
            abs(landedDistanceMeters - expected.distanceMeters) < 0.5,
            "expected=${expected.distanceMeters} actual=$landedDistanceMeters",
        )
    }

    @Test
    fun `un pas fait avancer la position avec la vitesse avant de freiner par la gravite`() {
        val start = FlightState(worldX = 0.0, worldY = 0.0, vx = 100.0, vy = 200.0, rotation = 0.0)
        val next = FlightSimulator.step(start, gravity = 950.0, rotationSpeed = 6.0, dt = 0.1)

        // worldX/worldY avancent avec vx/vy AVANT que vy soit décrémenté.
        assertTrue(abs(next.worldX - 10.0) < 1e-9)
        assertTrue(abs(next.worldY - 20.0) < 1e-9)
        assertTrue(abs(next.vy - (200.0 - 95.0)) < 1e-9)
        assertTrue(abs(next.rotation - 0.6) < 1e-9)
    }
}
