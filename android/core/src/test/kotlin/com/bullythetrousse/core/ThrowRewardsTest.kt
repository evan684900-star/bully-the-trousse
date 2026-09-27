package com.bullythetrousse.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThrowRewardsTest {
    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun `un gain alimente le total et la case du jour`() {
        val s = DailyStats.recordEarning(GameSave(totalMoneyEarned = 10), 40, today)
        assertEquals(50, s.totalMoneyEarned)
        assertEquals(40, s.dailyEarnings["2026-09-23"])
        val s2 = DailyStats.recordEarning(s, 5, today)
        assertEquals(45, s2.dailyEarnings["2026-09-23"])
    }

    @Test
    fun `un gain nul ne compte pas`() {
        val s = GameSave()
        assertEquals(s, DailyStats.recordEarning(s, 0, today))
    }

    @Test
    fun `sur la plage le gain compte aussi pour le billet de bus`() {
        val plage = DailyStats.recordEarning(GameSave(inPlage = true), 30, today)
        assertEquals(30, plage.plageMoneyEarned)
        val cour = DailyStats.recordEarning(GameSave(inPlage = false), 30, today)
        assertEquals(0, cour.plageMoneyEarned)
    }

    @Test
    fun `le record du jour ne garde que le meilleur`() {
        var s = DailyStats.recordDistance(GameSave(), 120.0, today)
        s = DailyStats.recordDistance(s, 80.0, today)
        assertEquals(120.0, s.dailyBestDistance["2026-09-23"])
        s = DailyStats.recordDistance(s, 300.0, today)
        assertEquals(300.0, s.dailyBestDistance["2026-09-23"])
    }

    @Test
    fun `seuls les sept derniers jours sont gardes`() {
        val map = mapOf(
            "2026-09-23" to 1, "2026-09-17" to 2, "2026-09-16" to 3, "pas-une-date" to 4,
        )
        assertEquals(setOf("2026-09-23", "2026-09-17"), DailyStats.prune(map, today).keys)
    }

    @Test
    fun `les sept derniers jours vont du plus ancien a aujourd hui`() {
        val keys = DailyStats.last7DayKeys(today)
        assertEquals(7, keys.size)
        assertEquals(LocalDate.of(2026, 9, 17), keys.first())
        assertEquals(today, keys.last())
    }

    @Test
    fun `le meilleur lancer de la semaine ignore les jours plus anciens`() {
        val best = DailyStats.weeklyBest(mapOf("2026-09-20" to 150.0, "2026-09-01" to 900.0), today)
        assertEquals(150.0, best)
        assertEquals(0.0, DailyStats.weeklyBest(emptyMap(), today))
    }

    @Test
    fun `un palier deja atteint ne rapporte rien`() {
        assertNull(Milestones.reward(GameSave(milestoneReached = 3), 350.0))
        assertNull(Milestones.reward(GameSave(), 99.9))
    }

    @Test
    fun `chaque palier franchi d un coup rapporte ses 50 dollars`() {
        val r = Milestones.reward(GameSave(milestoneReached = 1), 480.0)!!
        assertEquals(4, r.milestone)
        assertEquals(150, r.bonus)
        assertEquals(400, r.meters)
    }

    @Test
    fun `le bonus lunaire ne joue qu apres l espace`() {
        val lunar = Skins.find("lunaire")
        assertEquals(125, LunarBonus.apply(100, lunar, cameFromSpace = true))
        assertEquals(100, LunarBonus.apply(100, lunar, cameFromSpace = false))
        assertEquals(100, LunarBonus.apply(100, Skins.find("classique"), cameFromSpace = true))
    }
}
