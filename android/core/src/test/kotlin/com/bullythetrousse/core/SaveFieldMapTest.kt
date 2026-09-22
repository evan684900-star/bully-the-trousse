package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveFieldMapTest {
    @Test
    fun `un aller-retour par le dictionnaire de champs conserve la sauvegarde`() {
        val save = GameSave(
            money = 4200,
            puissanceLevel = 7,
            bestDistance = 812.5,
            ownedSkins = listOf("classique", "doree", "fer"),
            equippedSkin = "doree",
            pseudo = "Evan",
            unlockedAchievements = listOf("first", "rich"),
            dailyEarnings = mapOf("2026-09-22" to 900),
            dailyBestDistance = mapOf("2026-09-22" to 812.5),
            dailyChallenges = listOf(DailyChallenge(kind = "throws", target = 10.0, reward = 300, progress = 4.0)),
        )
        assertEquals(save, SaveCodec.fromFieldMap(SaveCodec.toFieldMap(save)))
    }

    @Test
    fun `les entiers restent des entiers et les distances des flottants`() {
        // Sinon le site relirait un entier là où il écrit un flottant (et
        // inversement) : deux modèles de données dans un seul projet.
        val fields = SaveCodec.toFieldMap(GameSave(money = 1500, bestDistance = 0.0, playTime = 42L))
        assertIs<Long>(fields["money"])
        assertIs<Long>(fields["playTime"])
        assertIs<Double>(fields["bestDistance"])
        assertIs<Boolean>(fields["musicMuted"])
        assertIs<String>(fields["equippedSkin"])
        assertIs<List<*>>(fields["ownedSkins"])
    }

    @Test
    fun `un document ecrit par le site se relit sans perte`() {
        // Forme exacte de ce que Firestore rend : des Long pour les entiers,
        // des Double pour les flottants, des listes et des dictionnaires
        // imbriqués — plus les champs techniques du site, qui ne font pas
        // partie de la sauvegarde.
        val fromSite = mapOf(
            "money" to 9001L,
            "bestDistance" to 1234.5,
            "equippedSkin" to "arcenciel",
            "ownedSkins" to listOf("classique", "arcenciel"),
            "musicMuted" to true,
            "dailyEarnings" to mapOf("2026-09-21" to 120L),
            "dailyChallenges" to listOf(
                mapOf("kind" to "distance", "target" to 500.0, "reward" to 250L, "progress" to 120.0, "claimed" to false),
            ),
            "sessionId" to "abc-123",
            "updatedAt" to "un Timestamp Firestore",
        )
        val save = SaveCodec.fromFieldMap(fromSite)
        assertEquals(9001, save.money)
        assertEquals(1234.5, save.bestDistance)
        assertEquals("arcenciel", save.equippedSkin)
        assertEquals(listOf("classique", "arcenciel"), save.ownedSkins)
        assertTrue(save.musicMuted)
        assertEquals(mapOf("2026-09-21" to 120), save.dailyEarnings)
        assertEquals(1, save.dailyChallenges.size)
        assertEquals("distance", save.dailyChallenges[0].kind)
        assertEquals(250, save.dailyChallenges[0].reward)
        // Les champs absents gardent leur valeur par défaut.
        assertEquals("classique", GameSave().equippedSkin)
        assertEquals(0, save.puissanceLevel)
    }

    @Test
    fun `un entier ecrit sans decimale se relit en distance`() {
        // Le site écrit un 0 en JS : Firestore le stocke en entier, alors que
        // le champ est un flottant côté Kotlin.
        val save = SaveCodec.fromFieldMap(mapOf("bestDistance" to 0L, "plageBestDistance" to 300L))
        assertEquals(0.0, save.bestDistance)
        assertEquals(300.0, save.plageBestDistance)
    }

    @Test
    fun `un document vide redonne une sauvegarde neuve`() {
        assertEquals(GameSave(), SaveCodec.fromFieldMap(emptyMap()))
    }
}
