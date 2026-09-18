package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DailyChallengesTest {
    @Test
    fun `rewardMultiplier vaut 1 sans aucun niveau achete`() {
        assertClose(1.0, DailyChallenges.rewardMultiplier(0, 0))
    }

    @Test
    fun `rewardMultiplier suit la meme formule que challengeRewardMultiplier cote web`() {
        // p=10, v=5 : (1+10*0.12)^2 * (1+5*0.09) * (1+(15)*0.03)
        val expected = Math.pow(1 + 10 * 0.12, 2.0) * (1 + 5 * 0.09) * (1 + 15 * 0.03)
        assertClose(expected, DailyChallenges.rewardMultiplier(10, 5))
    }

    @Test
    fun `generate exclut les defis volcan tant que le monde n'est pas debloque`() {
        val challenges = DailyChallenges.generate(volcanUnlocked = false, totalPuissance = 0, totalVitesse = 0) { 0.999 }
        assertTrue(challenges.none { it.kind == "volcan" || it.kind == "skid" })
        assertEquals(3, challenges.size)
    }

    @Test
    fun `generate applique le multiplicateur de recompense`() {
        // random() toujours 0.0 : Fisher-Yates déterministe (calculé à la main),
        // fait atterrir "distance" en tête ; premier tier de chaque défi tiré
        // (60m, 150$ de base).
        val challenges = DailyChallenges.generate(volcanUnlocked = false, totalPuissance = 10, totalVitesse = 5) { 0.0 }
        val multiplier = DailyChallenges.rewardMultiplier(10, 5)
        assertEquals("distance", challenges.first().kind)
        assertEquals(Math.round(150 * multiplier).toInt(), challenges.first().reward)
    }

    @Test
    fun `ensure regenere si la date a change ou si la liste est vide`() {
        val staleSave = GameSave(dailyChallengeDate = "2020-01-01", dailyChallenges = emptyList())
        val updated = DailyChallenges.ensure(staleSave, todayKey = "2026-09-18", totalPuissance = 0, totalVitesse = 0) { 0.0 }
        assertEquals("2026-09-18", updated.dailyChallengeDate)
        assertEquals(3, updated.dailyChallenges.size)
    }

    @Test
    fun `ensure ne touche a rien si la date est deja a jour et qu'il y a des defis`() {
        val existing = listOf(DailyChallenge(kind = "throws", target = 5.0, reward = 150))
        val save = GameSave(dailyChallengeDate = "2026-09-18", dailyChallenges = existing)
        val updated = DailyChallenges.ensure(save, todayKey = "2026-09-18", totalPuissance = 0, totalVitesse = 0)
        assertEquals(existing, updated.dailyChallenges)
    }

    @Test
    fun `bump en mode ADD cumule la progression, ignore les defis reclames ou d'un autre type`() {
        val save = GameSave(
            dailyChallenges = listOf(
                DailyChallenge(kind = "throws", target = 5.0, reward = 100, progress = 2.0),
                DailyChallenge(kind = "throws", target = 5.0, reward = 100, progress = 1.0, claimed = true),
                DailyChallenge(kind = "distance", target = 60.0, reward = 150, progress = 10.0),
            ),
        )
        val updated = DailyChallenges.bump(save, kind = "throws", amount = 1.0, mode = BumpMode.ADD)
        assertClose(3.0, updated.dailyChallenges[0].progress)
        assertClose(1.0, updated.dailyChallenges[1].progress) // réclamé : inchangé
        assertClose(10.0, updated.dailyChallenges[2].progress) // autre type : inchangé
    }

    @Test
    fun `bump en mode MAX garde la meilleure valeur`() {
        val save = GameSave(dailyChallenges = listOf(DailyChallenge(kind = "distance", target = 200.0, reward = 500, progress = 150.0)))
        val higher = DailyChallenges.bump(save, kind = "distance", amount = 120.0, mode = BumpMode.MAX)
        assertClose(150.0, higher.dailyChallenges[0].progress) // 120 < 150, ignoré
        val lower = DailyChallenges.bump(save, kind = "distance", amount = 180.0, mode = BumpMode.MAX)
        assertClose(180.0, lower.dailyChallenges[0].progress)
    }

    @Test
    fun `claim reussit une fois la cible atteinte et verse la recompense`() {
        val save = GameSave(money = 100, dailyChallenges = listOf(DailyChallenge(kind = "throws", target = 5.0, reward = 150, progress = 5.0)))
        val result = DailyChallenges.claim(save, index = 0)
        assertIs<DailyChallenges.ClaimResult.Success>(result)
        assertEquals(150, result.reward)
        assertEquals(250, result.save.money)
        assertTrue(result.save.dailyChallenges[0].claimed)
    }

    @Test
    fun `claim refuse si la cible n'est pas atteinte ou deja reclamee`() {
        val notReady = GameSave(dailyChallenges = listOf(DailyChallenge(kind = "throws", target = 5.0, reward = 150, progress = 2.0)))
        assertIs<DailyChallenges.ClaimResult.NotReady>(DailyChallenges.claim(notReady, index = 0))

        val alreadyClaimed = GameSave(dailyChallenges = listOf(DailyChallenge(kind = "throws", target = 5.0, reward = 150, progress = 5.0, claimed = true)))
        assertIs<DailyChallenges.ClaimResult.NotReady>(DailyChallenges.claim(alreadyClaimed, index = 0))

        assertIs<DailyChallenges.ClaimResult.NotReady>(DailyChallenges.claim(GameSave(), index = 0))
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
