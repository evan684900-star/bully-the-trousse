package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementsTest {
    @Test
    fun `il y a bien 60 succes, avec des ids uniques et leurs textes`() {
        assertEquals(60, Achievements.ALL.size)
        assertEquals(60, Achievements.ALL.map { it.id }.toSet().size)
        // Chaque succès a son nom et sa description (clés générées depuis le site).
        for (a in Achievements.ALL) {
            val (nameKey, descKey) = ACHIEVEMENT_KEYS.getValue(a.id)
            assertTrue(I18n.has(nameKey) && I18n.has(descKey), a.id)
        }
    }

    @Test
    fun `les succes de la Ville se debloquent aux memes seuils que sur le site`() {
        // La même partie que celle testée dans le navigateur côté site.
        val save = GameSave(
            villeUnlocked = true, villeThrows = 150, villeBestDistance = 12000.0, villePuddles = 3,
            villeMoneyEarned = 2_000_000, villeCrosswalkDeaths = 5, villeStairs = 2, ownedCosmetics = listOf("noeud"),
        )
        val ville = Achievements.newlyUnlocked(save).map { it.id }.filter { it.startsWith("ville") }.sorted()
        assertEquals(
            listOf("villeCosmetique", "villeDebloquee", "villePieton", "villeSalaire", "villeToit100", "villeToit10000m", "villeToit1000m"),
            ville,
        )
        val all = save.copy(villeStoryDone = true, villeBestDistance = 50_000.0, villePuddles = 25, villeMoneyEarned = 25_000_000,
            villeStairs = 10, ownedCosmetics = Cosmetics.ALL.map { it.id })
        assertEquals(13, Achievements.newlyUnlocked(all).count { it.id.startsWith("ville") })
    }

    @Test
    fun `newlyUnlocked ignore un succes deja debloque meme si sa condition est vraie`() {
        val save = GameSave(totalThrows = 5, unlockedAchievements = listOf("premierLancer"))
        val newly = Achievements.newlyUnlocked(save)
        assertFalse(newly.any { it.id == "premierLancer" })
    }

    @Test
    fun `newlyUnlocked detecte un succes qui vient de se declencher`() {
        val save = GameSave(bestDistance = 150.0)
        val newly = Achievements.newlyUnlocked(save)
        assertTrue(newly.any { it.id == "cent100m" })
        assertFalse(newly.any { it.id == "cinq500m" }) // 150m < 500m
    }

    @Test
    fun `apply ajoute les nouveaux succes sans dupliquer les anciens`() {
        val save = GameSave(totalThrows = 1, unlockedAchievements = listOf("premierLancer"))
        val updated = Achievements.apply(save)
        assertEquals(listOf("premierLancer"), updated.unlockedAchievements) // rien de nouveau
    }

    @Test
    fun `apply ne fait rien si aucun nouveau succes`() {
        val save = GameSave()
        val updated = Achievements.apply(save)
        assertEquals(save, updated)
    }

    @Test
    fun `collectionneur ne se declenche qu'une fois tous les skins possedes`() {
        val allButOne = Skins.ALL.dropLast(1).map { it.id }
        assertFalse(Achievements.ALL.first { it.id == "collectionneur" }.check(GameSave(ownedSkins = allButOne)))
        val all = Skins.ALL.map { it.id }
        assertTrue(Achievements.ALL.first { it.id == "collectionneur" }.check(GameSave(ownedSkins = all)))
    }

    @Test
    fun `toutesTrainees se declenche une fois toutes les trainees possedees`() {
        val all = Trails.ALL.map { it.id }
        assertTrue(Achievements.ALL.first { it.id == "toutesTrainees" }.check(GameSave(ownedTrails = all)))
    }

    @Test
    fun `defisAccomplis exige au moins 3 defis tous reclames`() {
        val notEnough = listOf(DailyChallenge(kind = "throws", target = 5.0, reward = 100, claimed = true))
        assertFalse(Achievements.ALL.first { it.id == "defisAccomplis" }.check(GameSave(dailyChallenges = notEnough)))

        val threeClaimed = listOf(
            DailyChallenge(kind = "throws", target = 5.0, reward = 100, claimed = true),
            DailyChallenge(kind = "distance", target = 60.0, reward = 150, claimed = true),
            DailyChallenge(kind = "earn", target = 300.0, reward = 150, claimed = true),
        )
        assertTrue(Achievements.ALL.first { it.id == "defisAccomplis" }.check(GameSave(dailyChallenges = threeClaimed)))

        val notAllClaimed = threeClaimed.mapIndexed { i, c -> if (i == 0) c.copy(claimed = false) else c }
        assertFalse(Achievements.ALL.first { it.id == "defisAccomplis" }.check(GameSave(dailyChallenges = notAllClaimed)))
    }
}
