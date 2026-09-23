package com.bullythetrousse.core

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VampireBoostTest {
    private val vampire = Skins.find("vampire")
    private val basique = Skins.find("basique")

    @Test
    fun `le boost ne demarre pas avec une trousse qui n est pas la vampire`() {
        val state = VampireBoost.start(VampireBoostState(), basique)
        assertFalse(state.boosting)
    }

    @Test
    fun `le boost demarre avec la trousse vampire`() {
        val state = VampireBoost.start(VampireBoostState(), vampire)
        assertTrue(state.boosting)
    }

    @Test
    fun `le boost ne redemarre pas une fois le budget epuise`() {
        val epuise = VampireBoostState(used = true, secondsLeft = 0.0)
        assertFalse(VampireBoost.start(epuise, vampire).boosting)
    }

    @Test
    fun `maintenir une seconde multiplie vx par le facteur de croissance`() {
        val (state, vx) = VampireBoost.step(VampireBoost.start(VampireBoostState(), vampire), 1.0, 100.0)
        assertEquals(100.0 * VampireBoost.GROWTH_PER_SEC, vx, 1e-9)
        assertEquals(2.0, state.secondsLeft, 1e-9)
        assertFalse(state.used)
    }

    @Test
    fun `relacher avant le plafond met en pause sans consommer le reste`() {
        val apres1s = VampireBoost.step(VampireBoost.start(VampireBoostState(), vampire), 1.0, 100.0).first
        val relache = VampireBoost.end(apres1s)
        assertFalse(relache.boosting)
        assertFalse(relache.used)
        assertEquals(2.0, relache.secondsLeft, 1e-9)
        // Le bouton reste disponible pour un nouvel appui.
        assertTrue(VampireBoost.start(relache, vampire).boosting)
    }

    @Test
    fun `le budget total est de trois secondes cumulees`() {
        var state = VampireBoost.start(VampireBoostState(), vampire)
        var vx = 100.0
        repeat(3) {
            val next = VampireBoost.step(state, 1.0, vx)
            state = next.first
            vx = next.second
        }
        assertEquals(0.0, state.secondsLeft, 1e-9)
        assertTrue(state.used)
        assertFalse(state.boosting)
        assertEquals(100.0 * VampireBoost.GROWTH_PER_SEC.pow(3.0), vx, 1e-9)
    }

    @Test
    fun `une frame plus longue que le budget restant n accelere que le temps disponible`() {
        val presqueFini = VampireBoostState(boosting = true, secondsLeft = 0.2)
        val (state, vx) = VampireBoost.step(presqueFini, 5.0, 100.0)
        assertEquals(100.0 * VampireBoost.GROWTH_PER_SEC.pow(0.2), vx, 1e-9)
        assertEquals(0.0, state.secondsLeft, 1e-9)
        assertTrue(state.used)
    }

    @Test
    fun `relacher sans avoir appuye ne consomme pas l utilisation du lancer`() {
        val jamaisAppuye = VampireBoostState(secondsLeft = 0.0)
        val state = VampireBoost.end(jamaisAppuye)
        assertFalse(state.used)
    }

    @Test
    fun `le bouton ne s affiche que pendant le vol d une trousse vampire non epuisee`() {
        val frais = VampireBoostState()
        assertTrue(VampireBoost.isVisible(frais, vampire, flying = true))
        assertFalse(VampireBoost.isVisible(frais, vampire, flying = false))
        assertFalse(VampireBoost.isVisible(frais, basique, flying = true))
        assertFalse(VampireBoost.isVisible(frais.copy(used = true), vampire, flying = true))
    }

    @Test
    fun `sans maintien vx est inchange`() {
        val (state, vx) = VampireBoost.step(VampireBoostState(), 1.0, 100.0)
        assertEquals(100.0, vx, 1e-9)
        assertEquals(VampireBoost.MAX_SECONDS, state.secondsLeft, 1e-9)
    }
}
