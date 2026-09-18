package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Valeurs de référence calculées directement à partir des formules JS de
 * index.html (voir lockPower/lockAccuracyAndLaunch).
 */
class PowerAndAccuracyTest {
    @Test
    fun `puissance oscille entre 0,4 et 1,0`() {
        assertClose(0.4, PowerAndAccuracy.powerFraction(0.0))
        assertClose(0.9781349112503158, PowerAndAccuracy.powerFraction(0.5))
        assertClose(0.4129545854356576, PowerAndAccuracy.powerFraction(1.2))
    }

    @Test
    fun `precision oscille entre -1 et 1, zero est parfait`() {
        assertClose(0.0, PowerAndAccuracy.accuracyValue(0.0))
        assertClose(0.852108021949363, PowerAndAccuracy.accuracyValue(0.3))
        assertClose(-0.2555411020268312, PowerAndAccuracy.accuracyValue(1.0))
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
