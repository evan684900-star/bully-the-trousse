package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Valeurs de référence calculées directement à partir des formules JS de
 * index.html (voir lockAccuracyAndLaunch), pour garantir que ce portage
 * Kotlin se comporte identiquement à la version web.
 */
class ThrowPhysicsTest {
    @Test
    fun `lancer parfait avec puissance et vitesse a zero`() {
        val result = ThrowPhysics.simulateThrow(
            ThrowInput(puissanceLevel = 0, vitesseLevel = 0, lockedPower = 1.0, accuracyValue = 0.0)
        )
        assertEquals(20.21052631578947, result.distanceMeters, absoluteTolerance = 1e-9)
        assertTrue(result.isPerfect)
        assertEquals(480.0, result.initialSpeed, absoluteTolerance = 1e-9)
        assertEquals(45.0, result.angleDegrees, absoluteTolerance = 1e-9)
        assertEquals(950.0, result.effectiveGravity, absoluteTolerance = 1e-9)
    }

    @Test
    fun `lancer avec niveaux et visee legerement decalee`() {
        val result = ThrowPhysics.simulateThrow(
            ThrowInput(puissanceLevel = 10, vitesseLevel = 5, lockedPower = 0.8, accuracyValue = 0.1)
        )
        assertEquals(90.63444199353906, result.distanceMeters, absoluteTolerance = 1e-9)
        assertTrue(!result.isPerfect) // 0.1 dépasse la fenêtre par défaut (0.08)
    }

    @Test
    fun `en dehors de la fenetre de lancer parfait`() {
        val result = ThrowPhysics.simulateThrow(
            ThrowInput(puissanceLevel = 0, vitesseLevel = 0, lockedPower = 1.0, accuracyValue = 0.2)
        )
        assertTrue(!result.isPerfect)
    }

    @Test
    fun `trousse Claude elargit la fenetre de lancer parfait`() {
        val result = ThrowPhysics.simulateThrow(
            ThrowInput(
                puissanceLevel = 0, vitesseLevel = 0, lockedPower = 1.0, accuracyValue = 0.1,
                perfectWindow = PhysicsConstants.PERFECT_WINDOW_CLAUDE,
            )
        )
        assertTrue(result.isPerfect)
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= absoluteTolerance,
            "expected=$expected actual=$actual",
        )
    }
}
