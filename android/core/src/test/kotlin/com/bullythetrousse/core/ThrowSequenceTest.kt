package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThrowSequenceTest {
    /** Horloge factice : renvoie les timestamps donnés, un par appel. */
    private fun fakeClock(vararg millis: Long): () -> Long {
        var index = 0
        return {
            val value = millis[index]
            if (index < millis.size - 1) index++
            value
        }
    }

    @Test
    fun `un tap depuis idle demarre la charge de puissance`() {
        val sequence = ThrowSequence(clock = fakeClock(0L))
        val state = sequence.tap(puissanceLevel = 0, vitesseLevel = 0)
        assertIs<ThrowState.ChargingPower>(state)
    }

    @Test
    fun `le 2e tap fige la puissance et demarre la charge de precision`() {
        // Premier tap à t=0 (idle -> charging power), deuxième à t=500ms.
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L))
        sequence.tap(puissanceLevel = 0, vitesseLevel = 0)
        val state = sequence.tap(puissanceLevel = 0, vitesseLevel = 0)

        assertIs<ThrowState.ChargingAccuracy>(state)
        val expectedPower = PowerAndAccuracy.powerFraction(0.5) // (500-0)/1000
        assertEquals(expectedPower, (state as ThrowState.ChargingAccuracy).lockedPower, absoluteTolerance = 1e-9)
    }

    @Test
    fun `le 3e tap calcule le lancer avec la puissance et la precision figees`() {
        // t=0 (idle->power), t=500ms (power->accuracy, lockedPower fixé),
        // t=1200ms (accuracy->landed, 700ms après le 2e tap).
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L, 1200L))
        sequence.tap(puissanceLevel = 10, vitesseLevel = 5)
        sequence.tap(puissanceLevel = 10, vitesseLevel = 5)
        val state = sequence.tap(puissanceLevel = 10, vitesseLevel = 5)

        assertIs<ThrowState.Landed>(state)
        val result = (state as ThrowState.Landed).result

        val expectedPower = PowerAndAccuracy.powerFraction(0.5)
        val expectedAccuracy = PowerAndAccuracy.accuracyValue(0.7) // (1200-500)/1000
        val expected = ThrowPhysics.simulateThrow(
            ThrowInput(puissanceLevel = 10, vitesseLevel = 5, lockedPower = expectedPower, accuracyValue = expectedAccuracy)
        )
        assertEquals(expected.distanceMeters, result.distanceMeters, absoluteTolerance = 1e-9)
    }

    @Test
    fun `un tap apres atterrissage relance un nouveau lancer`() {
        val sequence = ThrowSequence(clock = fakeClock(0L, 100L, 200L, 300L))
        sequence.tap(0, 0)
        sequence.tap(0, 0)
        sequence.tap(0, 0) // -> Landed
        val state = sequence.tap(0, 0)
        assertIs<ThrowState.ChargingPower>(state)
    }

    @Test
    fun `reset repart de idle`() {
        val sequence = ThrowSequence(clock = fakeClock(0L))
        sequence.tap(0, 0)
        sequence.reset()
        assertTrue(sequence.state is ThrowState.Idle)
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected=$expected actual=$actual")
    }
}
