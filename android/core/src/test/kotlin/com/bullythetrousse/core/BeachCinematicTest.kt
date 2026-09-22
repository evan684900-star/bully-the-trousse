package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun `le parcours complet se termine par finished=true, dialogues compris`() {
        // Un vrai déroulement doit faire avancer les dialogues (PARASOL,
        // CASTLE, TOWEL) à coups de tap, sinon la cinématique reste figée
        // dessus pour toujours — exactement comme côté web (bcUpdate n'a
        // aucun cas "dialog").
        var state = BeachCineState()
        var guard = 0
        while (!state.finished && guard < 100_000) {
            state = if (state.phase == BeachCinePhase.DIALOG) {
                BeachCinematic.advanceDialog(state)
            } else {
                BeachCinematic.step(state, 0.5)
            }
            guard++
        }
        assertTrue(state.finished)
        assertEquals(BeachCinePhase.OUTRO, state.phase)
    }

    @Test
    fun `sans taper, la cinematique reste bloquee sur un dialogue`() {
        var state = BeachCineState()
        var guard = 0
        while (state.phase != BeachCinePhase.DIALOG && guard < 1000) {
            state = BeachCinematic.step(state, 0.5)
            guard++
        }
        assertEquals(BeachCinePhase.DIALOG, state.phase)
        // Le temps continue de s'écouler localement, mais la phase ne bouge
        // plus, même après une éternité de jeu simulé.
        val stuck = BeachCinematic.step(state, 10_000.0)
        assertEquals(BeachCinePhase.DIALOG, stuck.phase)
        assertFalse(stuck.finished)
    }

    @Test
    fun `un dialogue a plusieurs repliques attend un tap par replique`() {
        // La fin de PARASOL ouvre un dialogue de deux répliques (bcineParasol1/2).
        var state = BeachCineState(phase = BeachCinePhase.PARASOL, phaseElapsed = BeachCinematic.PARASOL - 0.01)
        state = BeachCinematic.step(state, 0.02)
        assertEquals(BeachCinePhase.DIALOG, state.phase)
        assertEquals(2, state.dialogQueueRemaining)
        assertEquals(BeachCinePhase.FLY2, state.dialogNextPhase)

        state = BeachCinematic.advanceDialog(state)
        assertEquals(BeachCinePhase.DIALOG, state.phase) // encore une réplique à passer
        assertEquals(1, state.dialogQueueRemaining)

        state = BeachCinematic.advanceDialog(state)
        assertEquals(BeachCinePhase.FLY2, state.phase) // la dernière réplique reprend la cinématique
        assertEquals(0.0, state.phaseElapsed)
    }

    @Test
    fun `advanceDialog ne fait rien hors de la phase DIALOG`() {
        val state = BeachCineState(phase = BeachCinePhase.FLY1, phaseElapsed = 1.5)
        assertEquals(state, BeachCinematic.advanceDialog(state))
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

    @Test
    fun `un tir dans la zone verte interdite est refuse`() {
        // aimValue(t) = sin(t * 3.4) ; à t=1.3s elle vaut environ -0.958,
        // bien en dessous du seuil interdit (-0.6).
        val state = BeachCineState(phase = BeachCinePhase.AIM, phaseElapsed = 1.3)
        assertTrue(BeachCinematic.aimValue(1.3) <= BeachCinematic.AIM_FORBIDDEN_MAX)
        val result = BeachCinematic.tryLaunchFromAim(state)
        assertIs<AimLaunchResult.Forbidden>(result)
    }

    @Test
    fun `un tir hors de la zone verte est accepte et lance le vol`() {
        // Cherche un instant où aimValue dépasse le seuil interdit.
        var t = 0.0
        while (BeachCinematic.aimValue(t) <= BeachCinematic.AIM_FORBIDDEN_MAX) t += 0.05
        val state = BeachCineState(phase = BeachCinePhase.AIM, phaseElapsed = t)
        val result = BeachCinematic.tryLaunchFromAim(state)
        assertIs<AimLaunchResult.Launched>(result)
        assertEquals(BeachCinePhase.FLY1, result.state.phase)
        assertEquals(0.0, result.state.phaseElapsed)
    }

    @Test
    fun `tryLaunchFromAim ne fait rien hors de la phase AIM`() {
        val state = BeachCineState(phase = BeachCinePhase.CHASE, phaseElapsed = 1.0)
        val result = BeachCinematic.tryLaunchFromAim(state)
        assertIs<AimLaunchResult.Launched>(result)
        assertEquals(state, result.state)
    }

    @Test
    fun `sans tir manuel, AIM se termine quand meme au bout de 5 secondes`() {
        var state = BeachCineState(phase = BeachCinePhase.AIM, phaseElapsed = BeachCinematic.AIM - 0.01)
        state = BeachCinematic.step(state, 0.02)
        assertEquals(BeachCinePhase.FLY1, state.phase)
    }
}
