package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveMergeTest {
    @Test
    fun `le resume reprend pseudo argent record et niveau total`() {
        val s = SaveSummary.of(GameSave(pseudo = "", money = 12, bestDistance = 3.5, puissanceLevel = 2, vitesseLevel = 5))
        assertEquals(SaveSummary("-", 12, 3.5, 7), s)
    }

    @Test
    fun `les anciens noms de champs sont repris`() {
        val raw = mapOf("volcanoUnlocked" to true, "volcanoTutorialSeen" to true, "volcanoFailUntil" to 99L, "playtimeSeconds" to 3600)
        val s = LegacySave.migrate(GameSave(), raw)
        assertTrue(s.volcanUnlocked)
        assertTrue(s.volcanTutorialSeen)
        assertEquals(99L, s.volcanFailedUntil)
        assertEquals(3600L, s.playTime)
        // L'aide n'est proposée qu'à qui a échoué SANS avoir débloqué.
        assertFalse(s.volcanHelpAvailable)
        assertTrue(LegacySave.migrate(GameSave(), mapOf("volcanoEverFailed" to true)).volcanHelpAvailable)
    }

    @Test
    fun `une valeur deja presente n est jamais ecrasee`() {
        val s = LegacySave.migrate(GameSave(playTime = 10L, volcanFailedUntil = 5L), mapOf("playtimeSeconds" to 999, "volcanoFailUntil" to 7))
        assertEquals(10L, s.playTime)
        assertEquals(5L, s.volcanFailedUntil)
    }
}
