package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveCodecTest {
    @Test
    fun `une sauvegarde vide (aucun JSON) donne les valeurs par defaut`() {
        val save = SaveCodec.decodeOrDefault(null)
        assertEquals(GameSave(), save)
    }

    @Test
    fun `un JSON corrompu retombe sur les valeurs par defaut, sans planter`() {
        val save = SaveCodec.decodeOrDefault("{ pas du json valide")
        assertEquals(GameSave(), save)
    }

    @Test
    fun `encoder puis decoder redonne exactement la meme sauvegarde`() {
        val save = GameSave(
            money = 1234,
            puissanceLevel = 7,
            bestDistance = 88.5,
            ownedSkins = listOf("classique", "vampire"),
            equippedSkin = "vampire",
            dailyEarnings = mapOf("2026-09-18" to 300),
            dailyChallenges = listOf(DailyChallenge(kind = "throws", target = 5.0, reward = 150, progress = 2.0)),
        )
        val roundTripped = SaveCodec.decodeOrDefault(SaveCodec.encode(save))
        assertEquals(save, roundTripped)
    }

    @Test
    fun `un JSON auquel il manque des champs garde les valeurs par defaut pour ceux-la`() {
        // équivalent de Object.assign(defaultSave(), parsed) côté web : une
        // ancienne sauvegarde sans un champ récemment ajouté ne casse rien.
        val save = SaveCodec.decodeOrDefault("""{"money": 500, "equippedSkin": "vampire"}""")
        assertEquals(500, save.money)
        assertEquals("vampire", save.equippedSkin)
        assertEquals(GameSave().puissanceLevel, save.puissanceLevel)
        assertFalse(save.volcanUnlocked)
    }

    @Test
    fun `un JSON avec un champ inconnu (version plus recente) ne fait pas planter la lecture`() {
        val save = SaveCodec.decodeOrDefault("""{"money": 42, "champDuFutur": "quelque chose"}""")
        assertEquals(42, save.money)
        assertTrue(save.ownedSkins.contains("classique"))
    }
}
