package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VolcanoCinematicTest {
    @Test
    fun `chaque phase chronometree passe a la suivante une fois sa duree ecoulee, pas avant`() {
        var state = VolcanoCineState()
        state = VolcanoCinematic.step(state, VolcanoCinematic.FADE_DURATION - 0.01)
        assertEquals(CinePhase.FADE, state.phase)
        state = VolcanoCinematic.step(state, 0.02)
        assertEquals(CinePhase.APPROACH, state.phase)
        assertEquals(0.0, state.phaseElapsed)
    }

    @Test
    fun `la phase rocks demarre une roche apres le delai d'attente`() {
        var state = VolcanoCineState(phase = CinePhase.ROCKS)
        state = VolcanoCinematic.step(state, VolcanoCinematic.ROCK_WAIT - 0.01)
        assertEquals(RockState.WAIT, state.rockState)
        state = VolcanoCinematic.step(state, 0.02)
        assertEquals(RockState.WARN, state.rockState)
    }

    @Test
    fun `esquiver a temps ne coute pas de vie et resout la roche en cours`() {
        val state = VolcanoCineState(phase = CinePhase.ROCKS, rockState = RockState.WARN, lane = 0, lives = 2)
        val dodged = VolcanoCinematic.move(state, direction = 1)
        assertEquals(RockState.INCOMING, dodged.rockState)
        assertEquals(1, dodged.lane)
        assertEquals(2, dodged.lives) // esquive volontaire réussie : aucune vie perdue
    }

    @Test
    fun `ne pas esquiver a temps declenche une esquive automatique qui coute une vie`() {
        val state = VolcanoCineState(phase = CinePhase.ROCKS, rockState = RockState.WARN, rockElapsed = 0.0, lane = 0, lives = 2)
        val afterTimeout = VolcanoCinematic.step(state, VolcanoCinematic.ROCK_WARN) { 0.999 } // choisit le dernier canal possible
        assertEquals(RockState.INCOMING, afterTimeout.rockState)
        assertEquals(1, afterTimeout.lives)
        assertTrue(afterTimeout.lane != 0)
    }

    @Test
    fun `une roche non esquivee sans vie restante termine la cinematique en echec`() {
        val state = VolcanoCineState(phase = CinePhase.ROCKS, rockState = RockState.WARN, lane = 0, lives = 0)
        val afterTimeout = VolcanoCinematic.step(state, VolcanoCinematic.ROCK_WARN) { 0.0 }
        assertEquals(CinePhase.DEATH, afterTimeout.phase)
        assertEquals(0.0, afterTimeout.phaseElapsed)
    }

    @Test
    fun `la 5e roche esquivee fait passer directement a la descente`() {
        val state = VolcanoCineState(
            phase = CinePhase.ROCKS,
            rockState = RockState.GAP,
            rockElapsed = VolcanoCinematic.ROCK_GAP - 0.01,
            rockIndex = VolcanoCinematic.ROCKS, // les 5 roches sont déjà passées
        )
        val next = VolcanoCinematic.step(state, 0.02)
        assertEquals(CinePhase.DESCENT, next.phase)
        assertEquals(0.0, next.phaseElapsed)
    }

    @Test
    fun `atteindre le nombre de clics cible pendant la descente fait passer a l'atterrissage`() {
        var state = VolcanoCineState(phase = CinePhase.DESCENT, clicks = VolcanoCinematic.CLICK_TARGET - 1)
        state = VolcanoCinematic.click(state)
        assertEquals(VolcanoCinematic.CLICK_TARGET, state.clicks)
        val next = VolcanoCinematic.step(state, 0.016)
        assertEquals(CinePhase.LANDING, next.phase)
    }

    @Test
    fun `ne pas cliquer assez avant la fin de la descente termine la cinematique en echec`() {
        val state = VolcanoCineState(phase = CinePhase.DESCENT, clicks = 3)
        val timedOut = VolcanoCinematic.step(state, VolcanoCinematic.DESCENT_DURATION)
        assertEquals(CinePhase.DEATH, timedOut.phase)
    }

    @Test
    fun `la phase death se termine par l'issue FAILED`() {
        var state = VolcanoCineState(phase = CinePhase.DEATH)
        state = VolcanoCinematic.step(state, VolcanoCinematic.DEATH_DURATION - 0.01)
        assertNull(state.outcome)
        state = VolcanoCinematic.step(state, 0.02)
        assertEquals(VolcanoCineOutcome.FAILED, state.outcome)
    }

    @Test
    fun `la phase outro se termine par l'issue UNLOCKED`() {
        var state = VolcanoCineState(phase = CinePhase.OUTRO)
        state = VolcanoCinematic.step(state, VolcanoCinematic.OUTRO_DURATION)
        assertEquals(VolcanoCineOutcome.UNLOCKED, state.outcome)
    }

    @Test
    fun `une fois l'issue fixee, step() ne change plus rien`() {
        val finished = VolcanoCineState(phase = CinePhase.OUTRO, outcome = VolcanoCineOutcome.UNLOCKED)
        val after = VolcanoCinematic.step(finished, 10.0)
        assertEquals(finished, after)
    }

    @Test
    fun `applyOutcome debloque le monde volcan en cas de succes`() {
        val save = GameSave()
        val updated = VolcanoCinematic.applyOutcome(save, VolcanoCineOutcome.UNLOCKED, nowMillis = 1_000L)
        assertTrue(updated.volcanUnlocked)
        assertEquals("volcans", updated.currentWorld)
    }

    @Test
    fun `applyOutcome pose un cooldown de 10 minutes en cas d'echec`() {
        val save = GameSave()
        val updated = VolcanoCinematic.applyOutcome(save, VolcanoCineOutcome.FAILED, nowMillis = 1_000L)
        assertEquals(1_000L + VolcanoCinematic.RETRY_COOLDOWN_MILLIS, updated.volcanFailedUntil)
        assertTrue(updated.volcanHelpAvailable)
    }

    @Test
    fun `parcours complet en esquivant toujours a temps mene au deblocage`() {
        var state = VolcanoCineState()
        val dt = 1.0 / 60.0
        var guard = 0
        while (state.outcome == null && guard < 200_000) {
            if (state.phase == CinePhase.ROCKS && state.rockState == RockState.WARN) {
                // Bouge vers le centre à chaque fois plutôt que toujours dans le même
                // sens : sinon on finit collé à un bord (lane=1), où bouger encore vers
                // la droite est un no-op (comme "déjà collé à ce bord" côté web).
                val direction = if (state.lane <= 0) 1 else -1
                state = VolcanoCinematic.move(state, direction)
            } else if (state.phase == CinePhase.DESCENT) {
                state = VolcanoCinematic.click(state)
            }
            state = VolcanoCinematic.step(state, dt)
            guard++
        }
        assertEquals(VolcanoCineOutcome.UNLOCKED, state.outcome)
        assertEquals(2, state.lives) // aucune esquive automatique n'a jamais été nécessaire
    }
}
