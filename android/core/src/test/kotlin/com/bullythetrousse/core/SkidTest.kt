package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkidTest {
    @Test
    fun `shouldSkid suit le seuil de 25 pourcent`() {
        assertTrue(Skid.shouldSkid { 0.0 })
        assertTrue(Skid.shouldSkid { 0.24 })
        assertFalse(Skid.shouldSkid { 0.25 })
        assertFalse(Skid.shouldSkid { 0.9 })
    }

    @Test
    fun `targetWorldX tient dans les bornes 5 a 30 metres, mises a l'echelle`() {
        val min = Skid.targetWorldX(landingWorldX = 0.0) { 0.0 }
        val max = Skid.targetWorldX(landingWorldX = 0.0) { 1.0 }
        assertClose(5.0 * PhysicsConstants.SCALE, min)
        assertClose(30.0 * PhysicsConstants.SCALE, max)
    }

    @Test
    fun `un pas rapproche la trousse de la cible sans la depasser`() {
        var worldX = 0.0
        val target = 100.0
        repeat(50) {
            val step = Skid.step(worldX = worldX, targetWorldX = target, rotation = 0.0, dt = 1.0 / 60.0)
            assertTrue(step.worldX <= target)
            worldX = step.worldX
        }
        assertTrue(worldX > 0.0)
    }

    @Test
    fun `la glissade se termine quand il reste moins d'un pixel`() {
        val notFinished = Skid.step(worldX = 0.0, targetWorldX = 50.0, rotation = 0.0, dt = 0.016)
        assertFalse(notFinished.finished)
        val finished = Skid.step(worldX = 99.5, targetWorldX = 100.0, rotation = 0.0, dt = 0.016)
        assertTrue(finished.finished)
    }

    @Test
    fun `le cout de durabilite est applique une fois et declenche hasBrokenDurability a 0`() {
        val save = GameSave(durability = 5)
        val after = Skid.applyDurabilityCost(save)
        assertEquals(0, after.durability)
        assertTrue(after.hasBrokenDurability)
    }

    @Test
    fun `un derapage sans casser la durabilite ne met pas le flag`() {
        val save = GameSave(durability = 50)
        val after = Skid.applyDurabilityCost(save)
        assertEquals(40, after.durability)
        assertFalse(after.hasBrokenDurability)
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
