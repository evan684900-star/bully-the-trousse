package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeachCinematicTest {
    @Test
    fun `une phase passe a la suivante une fois sa duree ecoulee, pas avant`() {
        var state = BeachCineState()
        state = BeachCinematic.step(state, BeachCinematic.FADE - 0.01)
        assertEquals(BeachCinePhase.FADE, state.phase)
        state = BeachCinematic.step(state, 0.02)
        assertEquals(BeachCinePhase.HAIL, state.phase)
        assertEquals(0.0, state.phaseElapsed)
    }

    @Test
    fun `le parcours complet des 30 phases se termine par finished=true`() {
        var state = BeachCineState()
        var guard = 0
        while (!state.finished && guard < 100_000) {
            state = BeachCinematic.step(state, 0.5)
            guard++
        }
        assertTrue(state.finished)
        assertEquals(BeachCinePhase.OUTRO, state.phase)
    }

    @Test
    fun `step ne change plus rien une fois finished`() {
        val finished = BeachCineState(phase = BeachCinePhase.OUTRO, finished = true)
        assertEquals(finished, BeachCinematic.step(finished, 10.0))
    }

    @Test
    fun `applyOutcome debloque le monde et y depose le joueur`() {
        val save = GameSave(money = 5000)
        val updated = BeachCinematic.applyOutcome(save)
        assertTrue(updated.plageUnlocked)
        assertTrue(updated.inPlage)
        assertEquals("plage", updated.currentWorld)
        assertEquals(0, updated.money) // argent oublié à l'arrivée
        assertFalse(save.plageUnlocked) // la sauvegarde d'origine n'est pas mutée
    }
}
