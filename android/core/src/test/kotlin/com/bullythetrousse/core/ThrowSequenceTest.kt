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
    fun `perfectWindow personnalise (Trousse Claude) est bien transmis au calcul du lancer`() {
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L, 1200L))
        sequence.tap(puissanceLevel = 0, vitesseLevel = 0)
        sequence.tap(puissanceLevel = 0, vitesseLevel = 0)
        val state = sequence.tap(puissanceLevel = 0, vitesseLevel = 0, perfectWindow = PhysicsConstants.PERFECT_WINDOW_CLAUDE)

        val result = (state as ThrowState.Landed).result
        val expectedPower = PowerAndAccuracy.powerFraction(0.5)
        val expectedAccuracy = PowerAndAccuracy.accuracyValue(0.7)
        val expected = ThrowPhysics.simulateThrow(
            ThrowInput(
                puissanceLevel = 0, vitesseLevel = 0,
                lockedPower = expectedPower, accuracyValue = expectedAccuracy,
                perfectWindow = PhysicsConstants.PERFECT_WINDOW_CLAUDE,
            )
        )
        assertTrue(result.isPerfect == expected.isPerfect)
    }

    @Test
    fun `sans bounceChances, le 3e tap conclut toujours le lancer meme si le random serait favorable`() {
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L, 1200L))
        sequence.tap(0, 0)
        sequence.tap(0, 0)
        val state = sequence.tap(0, 0, random = { 0.0 }) // basketBounceChances vide par défaut
        assertIs<ThrowState.Landed>(state)
    }

    @Test
    fun `un rebond de la Trousse a Baskets relance une charge au lieu de conclure`() {
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L, 1200L))
        sequence.tap(0, 0)
        sequence.tap(0, 0)
        // random() < 0.20 (1er rebond) -> rebondit
        val state = sequence.tap(0, 0, basketBounceChances = Skins.BASKET_BOUNCE_CHANCES, random = { 0.1 })

        assertIs<ThrowState.ChargingPower>(state)
        val charging = state as ThrowState.ChargingPower
        assertTrue(charging.bounceCount == 1)
        assertTrue(charging.cumulativeDistanceMeters > 0.0)
    }

    @Test
    fun `la distance finale apres un rebond cumule les deux segments`() {
        // Segment 1 : 0 -> 500 (charge, 500ms) -> 1200 (précision, 700ms).
        // Segment 2, après rebond à t=1200 : 1200 -> 1700 (charge, 500ms) ->
        // 2400 (précision, 700ms) — mêmes écarts que le segment 1, donc
        // physiquement identique (même lockedPower, même accuracyValue).
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L, 1200L, 1700L, 2400L))
        sequence.tap(10, 5)
        sequence.tap(10, 5)
        val expectedSegment = ThrowPhysics.simulateThrow(
            ThrowInput(
                puissanceLevel = 10, vitesseLevel = 5,
                lockedPower = PowerAndAccuracy.powerFraction(0.5),
                accuracyValue = PowerAndAccuracy.accuracyValue(0.7),
            )
        )
        val afterBounce = sequence.tap(10, 5, basketBounceChances = Skins.BASKET_BOUNCE_CHANCES, random = { 0.0 })
        assertIs<ThrowState.ChargingPower>(afterBounce)

        sequence.tap(10, 5) // power -> accuracy pour le 2e segment
        val landed = sequence.tap(10, 5) // pas de bounceChances cette fois -> conclut

        assertIs<ThrowState.Landed>(landed)
        assertEquals(
            2 * expectedSegment.distanceMeters,
            (landed as ThrowState.Landed).result.distanceMeters,
            absoluteTolerance = 1e-6,
        )
    }

    @Test
    fun `un rebond ne se declenche jamais au-dela du nombre de chances disponibles (max 2 pour la Trousse a Baskets)`() {
        val sequence = ThrowSequence(clock = fakeClock(0L, 500L, 1200L, 1700L, 2400L, 2900L, 3600L))
        sequence.tap(0, 0)
        sequence.tap(0, 0)
        // 1er rebond (bounceCount 0 -> 1).
        val afterFirstBounce = sequence.tap(0, 0, basketBounceChances = Skins.BASKET_BOUNCE_CHANCES, random = { 0.0 })
        assertIs<ThrowState.ChargingPower>(afterFirstBounce)
        assertTrue((afterFirstBounce as ThrowState.ChargingPower).bounceCount == 1)

        sequence.tap(0, 0)
        // 2e rebond (bounceCount 1 -> 2, dernier disponible pour BASKET_BOUNCE_CHANCES).
        val afterSecondBounce = sequence.tap(0, 0, basketBounceChances = Skins.BASKET_BOUNCE_CHANCES, random = { 0.0 })
        assertIs<ThrowState.ChargingPower>(afterSecondBounce)
        assertTrue((afterSecondBounce as ThrowState.ChargingPower).bounceCount == 2)

        sequence.tap(0, 0)
        // bounceCount=2 >= basketBounceChances.size (2) -> ne rebondit plus jamais,
        // même avec un random() maximalement favorable.
        val landed = sequence.tap(0, 0, basketBounceChances = Skins.BASKET_BOUNCE_CHANCES, random = { 0.0 })
        assertIs<ThrowState.Landed>(landed)
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
